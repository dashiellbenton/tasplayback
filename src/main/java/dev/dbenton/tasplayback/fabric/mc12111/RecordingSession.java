package dev.dbenton.tasplayback.fabric.mc12111;

import dev.dbenton.tasplayback.core.branch.Segment;
import dev.dbenton.tasplayback.core.branch.SegmentGraph;
import dev.dbenton.tasplayback.core.event.EventRecord;
import dev.dbenton.tasplayback.core.format.AsyncRecorder;
import dev.dbenton.tasplayback.core.format.RecordingMeta;
import dev.dbenton.tasplayback.core.format.StreamId;
import dev.dbenton.tasplayback.core.format.TaspArchive;
import dev.dbenton.tasplayback.core.frame.FrameRecord;
import dev.dbenton.tasplayback.core.replay.HudEvents;
import dev.dbenton.tasplayback.core.replay.WorldEvents;
import dev.dbenton.tasplayback.core.time.GameTime;
import dev.dbenton.tasplayback.core.time.TimeKey;
import dev.dbenton.tasplayback.fabric.TASPlaybackFabric;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.packs.repository.Pack;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;

/**
 * One recording, possibly spanning several connections (each world join is a new segment).
 *
 * <p>All capture methods are called from game hooks and only read game state; they hand records to the
 * {@link AsyncRecorder}, which never blocks. Main-thread state is confined to the main thread; the
 * fields read by the network thread are volatile snapshots.
 */
final class RecordingSession {
    private final AsyncRecorder recorder;
    private final Path partDir;
    private final Path target;
    private final SegmentGraph graph = new SegmentGraph();
    private final InventoryTracker inventory = new InventoryTracker();
    private final PlayerState.StackMemo memo = new PlayerState.StackMemo();
    private final MenuState menuState = new MenuState();
    private final int thumbnailEvery;

    private PacketTee tee;
    private int segment = -1;
    private long tickCount;
    private long frameCounter;
    private long lastTickOfSegment;
    private volatile long nettyTick;
    private volatile long nettyFrame;

    private RecordingSession(AsyncRecorder recorder, Path partDir, Path target, int thumbnailEvery) {
        this.recorder = recorder;
        this.partDir = partDir;
        this.target = target;
        this.thumbnailEvery = thumbnailEvery;
    }

    static RecordingSession start(Minecraft mc, Path dir, int thumbnailEvery) throws IOException {
        String name = "rec-" + Instant.now().toString().replace(':', '-').replace('.', '-');
        Path part = dir.resolve(name + TaspArchive.PART_SUFFIX);
        Path target = dir.resolve(name + TaspArchive.EXTENSION);
        RecordingMeta meta = new RecordingMeta();
        meta.modVersion = TASPlaybackFabric.modVersion();
        meta.minecraftVersion = SharedConstants.getCurrentVersion().name();
        meta.loader = "fabric";
        meta.recordedAt = Instant.now().toString();
        meta.windowWidth = mc.getWindow().getScreenWidth();
        meta.windowHeight = mc.getWindow().getScreenHeight();
        meta.framebufferWidth = mc.getWindow().getWidth();
        meta.framebufferHeight = mc.getWindow().getHeight();
        meta.guiScale = mc.getWindow().getGuiScale();
        meta.language = mc.options.languageCode;
        meta.playerName = mc.getUser().getName();
        meta.playerUuid = String.valueOf(mc.getUser().getProfileId());
        for (Pack p : mc.getResourcePackRepository().getSelectedPacks()) {
            meta.resourcePacks.add(new RecordingMeta.ResourcePack(p.getId(), p.getTitle().getString(), ""));
        }
        meta.renderOptions.put("fov", (long) mc.options.fov().get().intValue());
        meta.renderOptions.put("bobView", mc.options.bobView().get());
        meta.renderOptions.put("fovEffectScale", mc.options.fovEffectScale().get());
        meta.renderOptions.put("distortionEffectScale", mc.options.screenEffectScale().get());
        meta.renderOptions.put("damageTiltStrength", mc.options.damageTiltStrength().get());
        meta.renderOptions.put("guiScale", (long) mc.options.guiScale().get().intValue());
        meta.renderOptions.put("chatScale", mc.options.chatScale().get());
        meta.renderOptions.put("chatWidth", mc.options.chatWidth().get());
        meta.renderOptions.put("chatOpacity", mc.options.chatOpacity().get());
        AsyncRecorder rec = AsyncRecorder.start(part, meta, new SegmentGraph(), new AsyncRecorder.Config());
        return new RecordingSession(rec, part, target, thumbnailEvery);
    }

    Path target() { return target; }
    AsyncRecorder recorder() { return recorder; }

    private TimeKey key(float partial) {
        if (!(partial >= 0f && partial < 1f)) partial = 0f;
        return new TimeKey(segment, tickCount, partial, frameCounter);
    }

    // ------------------------------------------------------------------ hooks

    /** A new singleplayer connection was opened: start a segment and tee its pipeline. */
    void onNewConnection(Connection connection, io.netty.channel.Channel channel) {
        int parent = segment;
        segment++;
        if (parent < 0) {
            graph.add(Segment.root(segment, GameTime.ZERO, frameCounter));
        } else {
            // A reconnect (world reload / savestate load). Without TAS-tool integration (M7) we cannot know
            // where the loaded state came from, so the boundary keeps all of the parent: a continuation.
            graph.add(Segment.rewind(segment, GameTime.ZERO, frameCounter, parent, new GameTime(lastTickOfSegment + 1, 0f),
                    "world-reload", "vanilla"));
        }
        recorder.publishSegments(graph);
        tickCount = 0;
        lastTickOfSegment = 0;
        nettyTick = 0;
        inventory.reset();
        menuState.reset();
        memo.main = net.minecraft.world.item.ItemStack.EMPTY;
        memo.off = net.minecraft.world.item.ItemStack.EMPTY;
        final int seg = segment;
        tee = new PacketTee(seg, (s, seq, bytes) ->
                recorder.offerEvent(StreamId.WORLD, WorldEvents.rawFrame(new TimeKey(s, nettyTick, 0f, nettyFrame), seq, bytes, 0, bytes.length)));
        tee.install(channel);
    }

    void onTickStart(Minecraft mc, boolean pauseFlag) {
        if (segment < 0) return;
        if (mc.level != null && !pauseFlag && mc.level.tickRateManager().runsNormally()) {
            tickCount++;
            lastTickOfSegment = tickCount;
            nettyTick = tickCount;
        }
    }

    void onTickEnd(Minecraft mc) {
        if (segment < 0 || mc.player == null || mc.level == null) return;
        Tripwire.enter();
        try {
            captureTick(mc);
        } finally {
            Tripwire.exit();
        }
    }

    private void captureTick(Minecraft mc) {
        ClientPacketListener conn = mc.getConnection();
        if (conn == null) return;
        TimeKey k = new TimeKey(segment, tickCount, 0f, frameCounter);
        recorder.offerEvent(StreamId.HUD, new EventRecord(k, HudEvents.PLAYER_TICK,
                PlayerState.capture(mc, mc.player, conn.registryAccess(), memo)));
        byte[] inv = inventory.diff(mc.player.getInventory(), conn.registryAccess());
        if (inv != null) recorder.offerEvent(StreamId.HUD, new EventRecord(k, HudEvents.INVENTORY, inv));
    }

    void onPredictedBlock(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state, int flags, int depth) {
        if (segment < 0) return;
        Tripwire.enter();
        try {
            dev.dbenton.tasplayback.core.io.ByteSink s = new dev.dbenton.tasplayback.core.io.ByteSink(24);
            s.writeLong(pos.asLong()).writeVarInt(net.minecraft.world.level.block.Block.getId(state)).writeVarInt(flags).writeVarInt(depth);
            recorder.offerEvent(StreamId.HUD, new EventRecord(new TimeKey(segment, tickCount, 0f, frameCounter), HudEvents.BLOCK_PREDICT, s.toByteArray()));
        } finally {
            Tripwire.exit();
        }
    }

    void onMainThreadPacket(Minecraft mc, Packet<?> packet) {
        if (tee == null) return;
        Tripwire.enter();
        try {
            notePacket(mc, packet);
        } finally {
            Tripwire.exit();
        }
    }

    private void notePacket(Minecraft mc, Packet<?> packet) {
        long seq = tee.takeSeq(packet);
        if (seq < 0) return;
        recorder.offerEvent(StreamId.WORLD, WorldEvents.deliver(key(mc.getDeltaTracker().getGameTimeDeltaPartialTick(true)), seq));
    }

    void onFrameRendered(Minecraft mc, float worldFov, float handFov) {
        if (segment < 0 || mc.level == null || mc.player == null) return;
        Tripwire.enter();
        try {
            captureFrame(mc, worldFov, handFov);
        } finally {
            Tripwire.exit();
        }
    }

    void onGuiEvent(Minecraft mc, int type, byte[] payload) {
        if (segment < 0) return;
        recorder.offerEvent(StreamId.GUI, new EventRecord(key(mc.getDeltaTracker().getGameTimeDeltaPartialTick(true)), type, payload));
    }

    private void captureFrame(Minecraft mc, float worldFov, float handFov) {
        if (mc.player.containerMenu != null && mc.getConnection() != null) {
            byte[] menu = menuState.capture(mc.player.containerMenu, mc.getConnection().registryAccess());
            if (menu != null) {
                recorder.offerEvent(StreamId.GUI, new EventRecord(key(mc.getDeltaTracker().getGameTimeDeltaPartialTick(true)),
                        dev.dbenton.tasplayback.core.replay.GuiEvents.MENU_STATE, menu));
            }
        }
        final TimeKey k = key(mc.getDeltaTracker().getGameTimeDeltaPartialTick(true));
        FrameRecord f = FrameCapture.capture(mc, k, worldFov, handFov);
        recorder.offerFrame(f);
        if (thumbnailEvery > 0 && frameCounter % thumbnailEvery == 0) {
            final long frame = frameCounter;
            Screenshot.takeScreenshot(mc.getMainRenderTarget(), img -> {
                try {
                    recorder.offerEvent(StreamId.HUD, thumbnail(frame, img).toRecord(k));
                    TaspClient.saveDebugImage(img, "rec", frame);
                } finally {
                    img.close();
                }
            });
        }
        frameCounter++;
        nettyFrame = frameCounter;
    }

    static HudEvents.Thumbnail thumbnail(long frame, NativeImage img) {
        int w = img.getWidth(), h = img.getHeight();
        byte[] rgba = new byte[w * h * 4];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = img.getPixel(x, y);
                int o = (y * w + x) * 4;
                rgba[o] = (byte) (argb >> 16); rgba[o + 1] = (byte) (argb >> 8); rgba[o + 2] = (byte) argb; rgba[o + 3] = (byte) (argb >>> 24);
            }
        }
        return HudEvents.Thumbnail.fromRgba(frame, w, h, rgba, 64, 36);
    }

    /** Stops recording and packs the {@code .tasp}. Blocking: call off the render thread. */
    void finish() throws IOException {
        recorder.close();
        TaspArchive.pack(partDir, target);
        TaspArchive.deletePartDirectory(partDir);
    }
}
