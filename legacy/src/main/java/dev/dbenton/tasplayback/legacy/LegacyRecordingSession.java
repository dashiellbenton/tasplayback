package dev.dbenton.tasplayback.legacy;

import dev.dbenton.tasplayback.core.branch.Segment;
import dev.dbenton.tasplayback.core.branch.SegmentGraph;
import dev.dbenton.tasplayback.core.event.EventRecord;
import dev.dbenton.tasplayback.core.format.AsyncRecorder;
import dev.dbenton.tasplayback.core.format.RecordingMeta;
import dev.dbenton.tasplayback.core.format.StreamId;
import dev.dbenton.tasplayback.core.format.TaspArchive;
import dev.dbenton.tasplayback.core.frame.FrameRecord;
import dev.dbenton.tasplayback.core.io.ByteSink;
import dev.dbenton.tasplayback.core.replay.HudEvents;
import dev.dbenton.tasplayback.core.replay.WorldEvents;
import dev.dbenton.tasplayback.core.time.GameTime;
import dev.dbenton.tasplayback.core.time.TimeKey;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraft.network.Packet;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.math.BlockPos;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;

/** One 1.12.2 recording; every world join is a segment. Capture is read-only and never blocks. */
final class LegacyRecordingSession {
    private final AsyncRecorder recorder;
    private final Path partDir;
    private final Path target;
    private final SegmentGraph graph = new SegmentGraph();
    private final LegacyInventoryTracker inventory = new LegacyInventoryTracker();
    private final LegacyPlayerState.Memo memo = new LegacyPlayerState.Memo();
    private final int thumbnailEvery;

    private int segment = -1;
    private long tickCount;
    private long frameCounter;
    private long lastTickOfSegment;

    private LegacyRecordingSession(AsyncRecorder recorder, Path partDir, Path target, int thumbnailEvery) {
        this.recorder = recorder;
        this.partDir = partDir;
        this.target = target;
        this.thumbnailEvery = thumbnailEvery;
    }

    static LegacyRecordingSession start(Minecraft mc, Path dir, int thumbnailEvery) throws IOException {
        String name = "rec-" + Instant.now().toString().replace(':', '-').replace('.', '-');
        Path part = dir.resolve(name + TaspArchive.PART_SUFFIX);
        Path target = dir.resolve(name + TaspArchive.EXTENSION);
        RecordingMeta meta = new RecordingMeta();
        meta.modVersion = TASPlaybackLegacy.glue().modVersion();
        meta.minecraftVersion = TASPlaybackLegacy.MC_VERSION;
        meta.loader = TASPlaybackLegacy.glue().loader();
        meta.recordedAt = Instant.now().toString();
        meta.windowWidth = mc.displayWidth;
        meta.windowHeight = mc.displayHeight;
        meta.framebufferWidth = mc.displayWidth;
        meta.framebufferHeight = mc.displayHeight;
        meta.guiScale = new net.minecraft.client.gui.ScaledResolution(mc).getScaleFactor();
        meta.language = mc.gameSettings.language;
        meta.playerName = mc.getSession().getProfile().getName();
        meta.playerUuid = String.valueOf(mc.getSession().getProfile().getId());
        meta.resourcePacks.add(new RecordingMeta.ResourcePack("vanilla", "Default", ""));
        for (ResourcePackRepository.Entry e : mc.getResourcePackRepository().getRepositoryEntries()) {
            meta.resourcePacks.add(new RecordingMeta.ResourcePack(e.getResourcePackName(), e.getResourcePackName(), ""));
        }
        meta.renderOptions.put("fov", (double) mc.gameSettings.fovSetting);
        meta.renderOptions.put("bobView", mc.gameSettings.viewBobbing);
        meta.renderOptions.put("guiScale", (long) mc.gameSettings.guiScale);
        meta.renderOptions.put("chatScale", (double) mc.gameSettings.chatScale);
        meta.renderOptions.put("chatWidth", (double) mc.gameSettings.chatWidth);
        meta.renderOptions.put("chatOpacity", (double) mc.gameSettings.chatOpacity);
        AsyncRecorder rec = AsyncRecorder.start(part, meta, new SegmentGraph(), new AsyncRecorder.Config());
        return new LegacyRecordingSession(rec, part, target, thumbnailEvery);
    }

    Path target() { return target; }

    private TimeKey key(float partial) {
        if (!(partial >= 0f && partial < 1f)) partial = 0f;
        return new TimeKey(segment, tickCount, partial, frameCounter);
    }

    void onNewConnection() {
        int parent = segment;
        segment++;
        if (parent < 0) {
            graph.add(Segment.root(segment, GameTime.ZERO, frameCounter));
        } else {
            graph.add(Segment.rewind(segment, GameTime.ZERO, frameCounter, parent, new GameTime(lastTickOfSegment + 1, 0f),
                    "world-reload", "vanilla"));
        }
        recorder.publishSegments(graph);
        tickCount = 0;
        lastTickOfSegment = 0;
        inventory.reset();
        memo.main = net.minecraft.item.ItemStack.EMPTY;
        memo.off = net.minecraft.item.ItemStack.EMPTY;
    }

    void onTickStart(Minecraft mc) {
        if (segment < 0) return;
        if (mc.world != null && !mc.isGamePaused()) {
            tickCount++;
            lastTickOfSegment = tickCount;
        }
    }

    void onTickEnd(Minecraft mc) {
        if (segment < 0 || mc.player == null || mc.world == null) return;
        TimeKey k = new TimeKey(segment, tickCount, 0f, frameCounter);
        recorder.offerEvent(StreamId.HUD, new EventRecord(k, HudEvents.PLAYER_TICK, LegacyPlayerState.capture(mc, mc.player, memo)));
        byte[] inv = inventory.diff(mc.player.inventory);
        if (inv != null) recorder.offerEvent(StreamId.HUD, new EventRecord(k, HudEvents.INVENTORY, inv));
    }

    void onMainThreadPacket(Packet<?> packet, float partial) {
        if (segment < 0) return;
        try {
            byte[] bytes = LegacyPackets.encode(packet);
            if (bytes != null) recorder.offerEvent(StreamId.WORLD, WorldEvents.packet(key(partial), bytes));
        } catch (IOException | RuntimeException e) {
            TASPlaybackLegacy.LOGGER.warn("Could not record packet {}", packet.getClass().getName(), e);
        }
    }

    void onLocalBlockChange(BlockPos pos, IBlockState state, int flags) {
        if (segment < 0) return;
        ByteSink s = new ByteSink(24);
        s.writeLong(pos.toLong()).writeVarInt(Block.getStateId(state)).writeVarInt(flags);
        recorder.offerEvent(StreamId.HUD, new EventRecord(new TimeKey(segment, tickCount, 0f, frameCounter), HudEvents.BLOCK_PREDICT, s.toByteArray()));
    }

    void onFrameRendered(Minecraft mc, float partial, float worldFov, float handFov) {
        if (segment < 0 || mc.world == null || mc.player == null) return;
        TimeKey k = key(partial);
        FrameRecord f = LegacyFrameCapture.capture(mc, k, partial, worldFov, handFov);
        recorder.offerFrame(f);
        if (thumbnailEvery > 0 && frameCounter % thumbnailEvery == 0) {
            recorder.offerEvent(StreamId.HUD, thumbnail(mc, frameCounter).toRecord(k));
        }
        frameCounter++;
    }

    /** Test-only: synchronous read-back of the framebuffer. */
    static HudEvents.Thumbnail thumbnail(Minecraft mc, long frame) {
        BufferedImage img = ScreenShotHelper.createScreenshot(mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
        LegacyClient.saveDebugImage(img, mc, LegacyClient.get().isReplaying() ? "rep" : "rec", frame);
        int w = img.getWidth(), h = img.getHeight();
        byte[] rgba = new byte[w * h * 4];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = img.getRGB(x, y);
                int o = (y * w + x) * 4;
                rgba[o] = (byte) (argb >> 16); rgba[o + 1] = (byte) (argb >> 8); rgba[o + 2] = (byte) argb; rgba[o + 3] = (byte) 255;
            }
        }
        return HudEvents.Thumbnail.fromRgba(frame, w, h, rgba, 64, 36);
    }

    void finish() throws IOException {
        recorder.close();
        TaspArchive.pack(partDir, target);
        TaspArchive.deletePartDirectory(partDir);
    }
}
