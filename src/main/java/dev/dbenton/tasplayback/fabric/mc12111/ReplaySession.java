package dev.dbenton.tasplayback.fabric.mc12111;

import dev.dbenton.tasplayback.core.branch.CanonicalResolver;
import dev.dbenton.tasplayback.core.branch.KeptRange;
import dev.dbenton.tasplayback.core.event.EventCodec;
import dev.dbenton.tasplayback.core.event.EventRecord;
import dev.dbenton.tasplayback.core.format.StreamId;
import dev.dbenton.tasplayback.core.format.TaspArchive;
import dev.dbenton.tasplayback.core.frame.FrameCodec;
import dev.dbenton.tasplayback.core.frame.FrameRecord;
import dev.dbenton.tasplayback.core.replay.HudEvents;
import dev.dbenton.tasplayback.core.replay.SegmentFeed;
import dev.dbenton.tasplayback.core.replay.TickTrack;
import dev.dbenton.tasplayback.core.time.TimeKey;
import dev.dbenton.tasplayback.core.timeline.Interp;
import dev.dbenton.tasplayback.core.timeline.PlaybackClock;
import dev.dbenton.tasplayback.fabric.TASPlaybackFabric;
import dev.dbenton.tasplayback.fabric.mc12111.mixin.MinecraftAccessor;
import dev.dbenton.tasplayback.fabric.mc12111.mixin.TimerAccessor;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Plays a recording back inside the real client (PLAN.md §6).
 *
 * <p>Each render frame, {@link #advance()} picks the recorded position for the current output time,
 * feeds the recorded packets that were handled before it, runs client ticks one at a time up to it
 * (restoring the recorded player state after each), and sets the partial tick. The camera, FOV,
 * perspective, GUI visibility and mouse position then come from the (interpolated) recorded frame.
 */
final class ReplaySession {
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
    private ReplayConnection conn;
    private long tickCount;
    private final PlayerState.StackMemo memo = new PlayerState.StackMemo();
    private double outTime;
    private long lastNanos = -1;
    double speed = 1.0;
    boolean paused;
    private int stepIndex;
    int shownIndex = -1;
    FrameRecord current;
    boolean finished;
    private boolean started;

    ReplaySession(Minecraft mc, Path file, Mode mode) throws IOException {
        this.mc = mc;
        this.file = file;
        this.mode = mode;
        TaspArchive.Recording rec = TaspArchive.open(file);
        this.problems = rec.problems();
        CanonicalResolver.Resolution res = CanonicalResolver.resolve(rec.segments);
        this.frames = res.filter(rec.stream(StreamId.FRAMES).decodeAll(new FrameCodec()), f -> f.key);
        if (frames.isEmpty()) throw new IOException("recording has no frames");
        List<TimeKey> keys = new ArrayList<>(frames.size());
        long[] wall = new long[frames.size()];
        for (int i = 0; i < frames.size(); i++) { keys.add(frames.get(i).key); wall[i] = frames.get(i).wallNanos; }
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
    long currentRecordedFrame() { return current == null ? -1 : current.key.frame; }

    private ClientPacketListener listener() { return mc.getConnection(); }

    private void openRange(int idx) {
        if (conn != null) conn.close();
        rangeIdx = idx;
        tickCount = 0;
        memo.main = net.minecraft.world.item.ItemStack.EMPTY;
        memo.off = net.minecraft.world.item.ItemStack.EMPTY;
        TASPlaybackFabric.LOGGER.info("Replay: opening segment {}", ranges.get(idx).kept.segment);
        conn = ReplayConnection.open(mc);
    }

    private int rangeOf(int frameIdx) {
        int seg = frames.get(frameIdx).key.segment;
        for (int r = Math.max(0, rangeIdx); r < ranges.size(); r++) if (ranges.get(r).kept.segment == seg) return r;
        return rangeIdx;
    }

    private final GuiReplay guiReplay = new GuiReplay();
    private final MenuState menuState = new MenuState();

    private void feedDue(long frame) {
        Range r = ranges.get(rangeIdx);
        // GUI input of a frame was polled before that frame's packets were processed: dispatch it first
        for (EventRecord e : r.gui.due(tickCount, frame)) guiReplay.apply(mc, e, menuState);
        for (byte[] f : r.feed.framesDue(tickCount, frame)) conn.feed(f);
        conn.drainOutbound();
    }

    private void applyHud() {
        ClientPacketListener l = listener();
        if (l == null || mc.player == null) return;
        for (EventRecord e : ranges.get(rangeIdx).hud.upTo(tickCount)) {
            if (e.type == HudEvents.PLAYER_TICK) PlayerState.apply(mc, mc.player, l.registryAccess(), e.payload, memo);
            else if (e.type == HudEvents.INVENTORY) InventoryTracker.apply(mc.player.getInventory(), l.registryAccess(), e.payload);
            else if (e.type == HudEvents.BLOCK_PREDICT && mc.level != null) {
                dev.dbenton.tasplayback.core.io.ByteSource in = new dev.dbenton.tasplayback.core.io.ByteSource(e.payload);
                net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.of(in.readLong());
                net.minecraft.world.level.block.state.BlockState st = net.minecraft.world.level.block.Block.stateById(in.readVarInt());
                mc.level.setBlock(pos, st, in.readVarInt(), in.readVarInt());
            }
        }
    }

    /** Called instead of the vanilla timer each render frame. Runs the replay up to the frame to show. */
    void advance() {
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

        int r = rangeOf(i);
        if (r != rangeIdx) openRange(r);
        FrameRecord a = frames.get(i);
        FrameRecord b = i + 1 < frames.size() && frames.get(i + 1).key.segment == a.key.segment ? frames.get(i + 1) : a;
        double g = a.key.ticks() + alpha * (b.key.ticks() - a.key.ticks());
        long targetTick = (long) Math.floor(g);
        float partial = (float) (g - targetTick);

        feedDue(a.key.frame);
        int guard = 0;
        while (tickCount < targetTick && guard++ < MAX_TICKS_PER_FRAME) {
            mc.tick();
            tickCount++;
            conn.drainOutbound();
            applyHud();
            feedDue(a.key.frame);
        }
        TimerAccessor t = (TimerAccessor) (Object) ((MinecraftAccessor) mc).tasp$getTimer();
        t.tasp$setResidual(partial);
        t.tasp$setPausedResidual(partial);
        current = alpha > 0 ? Interp.frames(a, b, (float) alpha) : a;
        shownIndex = i;
        applyFrameState();
    }

    /** Per-frame state the renderer reads directly from options/input. */
    private void applyFrameState() {
        FrameRecord f = current;
        if (f == null) return;
        CameraType[] types = CameraType.values();
        if (f.perspective >= 0 && f.perspective < types.length && mc.options.getCameraType() != types[f.perspective]) {
            mc.options.setCameraType(types[f.perspective]);
        }
        mc.options.hideGui = (f.flags & FrameRecord.FLAG_HIDE_GUI) != 0;
        boolean debug = (f.flags & FrameRecord.FLAG_DEBUG_OVERLAY) != 0;
        if (mc.debugEntries.isOverlayVisible() != debug) mc.debugEntries.setOverlayVisible(debug);
        TaspHooks.applyMouse(mc, f);
        if (mc.level != null) hadLevel = true;
    }

    /** True once the replay world existed; if it disappears afterwards the user disconnected. */
    boolean hadLevel;

    void togglePause() { paused = !paused; }

    void step(int frames) {
        paused = true;
        if (mode == Mode.STEP_FRAMES) {
            stepIndex = Math.max(0, Math.min(this.frames.size() - 1, stepIndex + frames));
        } else {
            int target = Math.max(0, Math.min(this.frames.size() - 1, Math.max(0, shownIndex) + frames));
            outTime = clock.frameTime(target);
        }
    }

    void close() {
        if (conn != null) conn.close();
        conn = null;
    }

    String status() {
        double t = mode == Mode.STEP_FRAMES ? clock.frameTime(Math.max(0, shownIndex)) : outTime;
        return String.format("TAS replay %s %.2f / %.2f s  x%.2f  frame %d/%d%s", paused ? "||" : ">",
                t, clock.duration(), speed, Math.max(0, shownIndex) + 1, frames.size(), finished ? "  (end)" : "");
    }
}
