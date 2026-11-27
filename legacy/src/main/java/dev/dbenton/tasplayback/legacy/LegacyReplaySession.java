package dev.dbenton.tasplayback.legacy;

import dev.dbenton.tasplayback.core.branch.CanonicalResolver;
import dev.dbenton.tasplayback.core.branch.KeptRange;
import dev.dbenton.tasplayback.core.event.EventCodec;
import dev.dbenton.tasplayback.core.event.EventRecord;
import dev.dbenton.tasplayback.core.format.StreamId;
import dev.dbenton.tasplayback.core.format.TaspArchive;
import dev.dbenton.tasplayback.core.frame.FrameCodec;
import dev.dbenton.tasplayback.core.frame.FrameRecord;
import dev.dbenton.tasplayback.core.io.ByteSource;
import dev.dbenton.tasplayback.core.replay.HudEvents;
import dev.dbenton.tasplayback.core.replay.SegmentFeed;
import dev.dbenton.tasplayback.core.replay.TickTrack;
import dev.dbenton.tasplayback.core.time.TimeKey;
import dev.dbenton.tasplayback.core.timeline.Interp;
import dev.dbenton.tasplayback.core.timeline.PlaybackClock;
import dev.dbenton.tasplayback.legacy.mixin.MinecraftAccessor;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.util.Timer;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 1.12.2 playback: a play handler on a network manager with no server behind it. Recorded packets are
 * decoded and processed on the main thread at their recorded points; ticks and the partial tick come from
 * the recording; the local player's state is restored after every tick.
 */
final class LegacyReplaySession {
    enum Mode { REALTIME, STEP_FRAMES }

    private static final int MAX_TICKS_PER_FRAME = 200;

    private final Minecraft mc;
    final Path file;
    final Mode mode;
    private final List<FrameRecord> frames;
    final PlaybackClock clock;
    private final List<Range> ranges = new ArrayList<>();
    final Map<Long, HudEvents.Thumbnail> recordedThumbnails = new HashMap<>();
    final List<String> problems;
    private final com.mojang.authlib.GameProfile profile;

    private static final class Range {
        final KeptRange kept;
        final SegmentFeed feed;
        final TickTrack hud;
        final dev.dbenton.tasplayback.core.replay.KeyedTrack gui;
        Range(KeptRange kept, SegmentFeed feed, TickTrack hud, dev.dbenton.tasplayback.core.replay.KeyedTrack gui) {
            this.kept = kept; this.feed = feed; this.hud = hud; this.gui = gui;
        }
    }

    private int rangeIdx = -1;
    private EmbeddedChannel channel;
    private NetHandlerPlayClient handler;
    private long tickCount;
    private final LegacyPlayerState.Memo memo = new LegacyPlayerState.Memo();
    private double outTime;
    private long lastNanos = -1;
    double speed = 1.0;
    boolean paused;
    private int stepIndex;
    int shownIndex = -1;
    FrameRecord current;
    boolean finished;
    boolean hadWorld;
    private boolean started;

    LegacyReplaySession(Minecraft mc, Path file, Mode mode) throws IOException {
        this.mc = mc;
        this.file = file;
        this.mode = mode;
        TaspArchive.Recording rec = TaspArchive.open(file);
        this.problems = rec.problems();
        // the replayed player must be the recorded one (default skin and name come from the profile)
        com.mojang.authlib.GameProfile own = mc.getSession().getProfile();
        java.util.UUID id = own.getId();
        try { if (!rec.meta.playerUuid.isEmpty()) id = java.util.UUID.fromString(rec.meta.playerUuid); } catch (IllegalArgumentException ignored) { }
        this.profile = new com.mojang.authlib.GameProfile(id, rec.meta.playerName.isEmpty() ? own.getName() : rec.meta.playerName);
        CanonicalResolver.Resolution res = CanonicalResolver.resolve(rec.segments);
        this.frames = res.filter(rec.stream(StreamId.FRAMES).decodeAll(new FrameCodec()), f -> f.key);
        if (frames.isEmpty()) throw new IOException("recording has no frames");
        List<TimeKey> keys = new ArrayList<>(frames.size());
        for (FrameRecord f : frames) keys.add(f.key);
        this.clock = PlaybackClock.realTime(keys);
        List<EventRecord> world = rec.stream(StreamId.WORLD).decodeAll(new EventCodec());
        List<EventRecord> hud = rec.stream(StreamId.HUD).decodeAll(new EventCodec());
        List<EventRecord> gui = rec.stream(StreamId.GUI).decodeAll(new EventCodec());
        for (EventRecord e : hud) {
            if (e.type == HudEvents.THUMBNAIL) {
                HudEvents.Thumbnail t = HudEvents.Thumbnail.from(e);
                recordedThumbnails.put(t.frame, t);
            }
        }
        for (final KeptRange kr : res.ranges) {
            List<EventRecord> segWorld = new ArrayList<>();
            for (EventRecord e : world) if (e.key.segment == kr.segment) segWorld.add(e);
            List<EventRecord> segHud = new ArrayList<>();
            for (EventRecord e : hud) if (e.key.segment == kr.segment && e.type != HudEvents.THUMBNAIL) segHud.add(e);
            List<EventRecord> segGui = new ArrayList<>();
            for (EventRecord e : gui) if (e.key.segment == kr.segment) segGui.add(e);
            ranges.add(new Range(kr, new SegmentFeed(segWorld, kr::contains), new TickTrack(segHud, e -> kr.contains(e.key)),
                    new dev.dbenton.tasplayback.core.replay.KeyedTrack(segGui, e -> kr.contains(e.key))));
        }
    }

    int frameCount() { return frames.size(); }

    private void openRange(int idx) {
        closeConnection();
        rangeIdx = idx;
        tickCount = 0;
        memo.main = net.minecraft.item.ItemStack.EMPTY;
        memo.off = net.minecraft.item.ItemStack.EMPTY;
        TASPlaybackLegacy.LOGGER.info("Replay: opening segment {}", ranges.get(idx).kept.segment);
        NetworkManager nm = new NetworkManager(EnumPacketDirection.CLIENTBOUND);
        channel = new EmbeddedChannel(nm);
        nm.setConnectionState(EnumConnectionState.PLAY);
        TASPlaybackLegacy.glue().prepareReplayChannel(channel, nm);
        handler = new NetHandlerPlayClient(mc, null, nm, profile);
        nm.setNetHandler(handler);
    }

    private void closeConnection() {
        if (channel != null) {
            drainOutbound();
            channel.close();
            channel = null;
        }
    }

    private void drainOutbound() {
        if (channel == null) return;
        channel.runPendingTasks();
        Object o;
        while ((o = channel.readOutbound()) != null) ReferenceCountUtil.release(o);
    }

    /** Recorded frame the replay is advancing to (bound for GUI event dispatch inside ticks). */
    private long frameBound;

    /** Dispatches GUI events that are due now (also called from GuiScreen.handleInput inside replay ticks). */
    void dispatchDueGui() {
        if (rangeIdx < 0) return;
        for (EventRecord e : ranges.get(rangeIdx).gui.due(tickCount, frameBound)) {
            try {
                LegacyGui.apply(mc, e);
            } catch (IOException | RuntimeException ex) {
                TASPlaybackLegacy.LOGGER.warn("Replay: GUI event failed", ex);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void feedDue(long frame) throws IOException {
        for (byte[] f : ranges.get(rangeIdx).feed.framesDue(tickCount, frame)) {
            Packet<INetHandlerPlayClient> p = (Packet<INetHandlerPlayClient>) LegacyPackets.decode(f);
            p.processPacket(handler);
        }
        drainOutbound();
    }

    private void applyHud() throws IOException {
        if (mc.player == null) return;
        for (EventRecord e : ranges.get(rangeIdx).hud.upTo(tickCount)) {
            if (e.type == HudEvents.PLAYER_TICK) LegacyPlayerState.apply(mc, mc.player, e.payload, memo);
            else if (e.type == HudEvents.INVENTORY) LegacyInventoryTracker.apply(mc.player.inventory, e.payload);
            else if (e.type == HudEvents.BLOCK_PREDICT && mc.world != null) {
                ByteSource in = new ByteSource(e.payload);
                BlockPos pos = BlockPos.fromLong(in.readLong());
                mc.world.setBlockState(pos, Block.getStateById(in.readVarInt()), in.readVarInt());
            }
        }
    }

    /** Replaces the timer each frame: runs the replay up to the frame to show and sets the partial tick. */
    void advance(Timer timer) throws IOException {
        long now = System.nanoTime();
        if (!started) { started = true; lastNanos = now; }
        int i;
        double alpha = 0;
        if (mode == Mode.STEP_FRAMES) {
            i = Math.min(stepIndex, frames.size() - 1);
            if (!paused && stepIndex < frames.size()) stepIndex++;
            else if (stepIndex >= frames.size()) finished = true;
        } else {
            if (!paused) outTime += (now - lastNanos) / 1e9 * speed;
            if (outTime >= clock.duration()) { outTime = clock.duration(); finished = true; }
            i = Math.max(0, clock.frameAt(outTime));
            alpha = clock.alphaAt(i, outTime);
        }
        lastNanos = now;

        int seg = frames.get(i).key.segment;
        int r = rangeIdx;
        for (int k = Math.max(0, rangeIdx); k < ranges.size(); k++) if (ranges.get(k).kept.segment == seg) { r = k; break; }
        if (r != rangeIdx) openRange(r);
        FrameRecord a = frames.get(i);
        FrameRecord b = i + 1 < frames.size() && frames.get(i + 1).key.segment == a.key.segment ? frames.get(i + 1) : a;
        double g = a.key.ticks() + alpha * (b.key.ticks() - a.key.ticks());
        long targetTick = (long) Math.floor(g);
        float partial = (float) (g - targetTick);

        frameBound = a.key.frame;
        dispatchDueGui();
        feedDue(a.key.frame);
        int guard = 0;
        while (tickCount < targetTick && guard++ < MAX_TICKS_PER_FRAME) {
            tickCount++; // GUI input of this tick is recorded with the in-progress count; it is dispatched inside runTick
            mc.runTick();
            drainOutbound();
            applyHud();
            feedDue(a.key.frame);
        }
        dispatchDueGui(); // leftovers, e.g. input while the game was paused (no ticks counted)
        timer.elapsedTicks = 0;
        timer.renderPartialTicks = partial;
        ((MinecraftAccessor) mc).tasp$setPausedPartial(partial);
        current = alpha > 0 ? Interp.frames(a, b, (float) alpha) : a;
        shownIndex = i;
        mc.gameSettings.thirdPersonView = current.perspective;
        mc.gameSettings.hideGUI = (current.flags & FrameRecord.FLAG_HIDE_GUI) != 0;
        mc.gameSettings.showDebugInfo = (current.flags & FrameRecord.FLAG_DEBUG_OVERLAY) != 0;
        if (mc.currentScreen != null && current != null) {
            // hover highlights and tooltips read the real cursor: move it to the recorded position
            int lx = (int) Math.round(current.mouseX * mc.displayWidth / Math.max(1, current.windowW));
            int ly = mc.displayHeight - 1 - (int) Math.round(current.mouseY * mc.displayHeight / Math.max(1, current.windowH));
            org.lwjgl.input.Mouse.setCursorPosition(lx, ly);
        }
        if (mc.world != null) hadWorld = true;
    }

    void togglePause() { paused = !paused; }

    void step(int n) {
        paused = true;
        if (mode == Mode.STEP_FRAMES) stepIndex = Math.max(0, Math.min(frames.size() - 1, stepIndex + n));
        else outTime = clock.frameTime(Math.max(0, Math.min(frames.size() - 1, Math.max(0, shownIndex) + n)));
    }

    void close() { closeConnection(); }

    String status() {
        double t = mode == Mode.STEP_FRAMES ? clock.frameTime(Math.max(0, shownIndex)) : outTime;
        return String.format("TAS replay %s %.2f / %.2f s  x%.2f  frame %d/%d%s", paused ? "||" : ">",
                t, clock.duration(), speed, Math.max(0, shownIndex) + 1, frames.size(), finished ? "  (end)" : "");
    }
}
