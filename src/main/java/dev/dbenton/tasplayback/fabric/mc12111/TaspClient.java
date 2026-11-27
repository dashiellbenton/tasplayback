package dev.dbenton.tasplayback.fabric.mc12111;

import dev.dbenton.tasplayback.core.json.Json;
import dev.dbenton.tasplayback.core.replay.HudEvents;
import dev.dbenton.tasplayback.fabric.TASPlaybackFabric;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Central state for the 1.21.11 client: at most one recording and at most one replay at a time.
 * Every hook enters here; when neither is active, every hook is a no-op.
 *
 * <p>Default keys (raw, so no key-binding API is needed on any node): F8 toggles recording
 * (it starts on the next world join, because a recording begins with the connection), and during
 * replay Space pauses, Left/Right step frames, Up/Down change speed.
 */
public final class TaspClient {
    private static final TaspClient INSTANCE = new TaspClient();

    public static TaspClient get() { return INSTANCE; }

    private final Minecraft mc = Minecraft.getInstance();
    private RecordingSession recording;
    private ReplaySession replay;
    private boolean armed = Boolean.getBoolean("tasplayback.autoRecord");
    private final int thumbnailEvery = Integer.getInteger("tasplayback.verifyEvery", 0);
    private final Map<Integer, Boolean> keyWasDown = new LinkedHashMap<>();
    private float lastWorldFov = 70f, lastHandFov = 70f;
    private final List<Double> verifyDiffs = new ArrayList<>();
    private boolean autoplayDone;
    private net.minecraft.client.tutorial.TutorialSteps savedTutorial;

    private TaspClient() {}

    public Path recordingsDir() throws IOException {
        Path p = mc.gameDirectory.toPath().resolve("tasplayback").resolve("recordings");
        Files.createDirectories(p);
        return p;
    }

    public boolean isReplaying() { return replay != null; }
    public boolean isRecording() { return recording != null; }
    ReplaySession replay() { return replay; }

    // ------------------------------------------------------------------ recording hooks

    public void onLocalConnection(Connection connection, io.netty.channel.Channel channel) {
        if (replay != null) return;
        if (recording == null && armed) {
            try {
                DebugText.reset();
                recording = RecordingSession.start(mc, recordingsDir(), thumbnailEvery);
                TASPlaybackFabric.LOGGER.info("Recording to {}", recording.target());
            } catch (IOException e) {
                TASPlaybackFabric.LOGGER.error("Could not start recording", e);
                armed = false;
                return;
            }
        }
        if (recording != null) recording.onNewConnection(connection, channel);
    }

    public void onTickStart() {
        DevTest.tick(mc);
        if (recording != null) recording.onTickStart(mc, ((dev.dbenton.tasplayback.fabric.mc12111.mixin.MinecraftAccessor) mc).tasp$isPauseFlag());
    }

    public void onTickEnd() {
        if (recording != null) recording.onTickEnd(mc);
        if (replay == null) pollRecordingKeys();
        maybeAutoplay();
    }

    void onGuiEvent(int type, byte[] payload) {
        if (recording != null) recording.onGuiEvent(mc, type, payload);
    }

    public void onPredictedBlock(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state, int flags, int depth) {
        if (recording != null) recording.onPredictedBlock(pos, state, flags, depth);
    }

    public void onMainThreadPacket(Packet<?> packet) {
        if (recording != null) recording.onMainThreadPacket(mc, packet);
    }

    public void onFrameRendered() {
        if (recording != null) recording.onFrameRendered(mc, lastWorldFov, lastHandFov);
        if (replay != null) afterReplayFrame();
    }

    /** getFov hook: remembers the value while recording, replaces it during replay. */
    public float fov(boolean world, float vanilla) {
        if (replay != null && replay.current != null) return world ? replay.current.fov : replay.current.handFov;
        if (world) lastWorldFov = vanilla; else lastHandFov = vanilla;
        return vanilla;
    }

    private boolean pressed(int key) {
        boolean down = InputConstants.isKeyDown(mc.getWindow(), key);
        Boolean was = keyWasDown.put(key, down);
        return down && (was == null || !was);
    }

    private void pollRecordingKeys() {
        if (mc.screen != null) return;
        if (pressed(GLFW.GLFW_KEY_F8)) {
            if (recording != null) {
                stopRecording();
            } else {
                armed = !armed;
                message(armed ? "TASPlayback: recording starts on the next world join (rejoin the world to start now)"
                        : "TASPlayback: recording disarmed");
            }
        }
    }

    public void stopRecording() {
        final RecordingSession r = recording;
        if (r == null) return;
        recording = null;
        armed = false;
        message("TASPlayback: saving recording...");
        Thread t = new Thread(() -> {
            try {
                r.finish();
                TASPlaybackFabric.LOGGER.info("Saved recording {}", r.target());
                mc.execute(() -> message("TASPlayback: saved " + r.target().getFileName()));
            } catch (IOException e) {
                TASPlaybackFabric.LOGGER.error("Saving recording failed", e);
            }
        }, "TASPlayback-Finish");
        t.start();
    }

    /** Stops and saves synchronously (tests). */
    void stopRecordingBlocking() {
        RecordingSession r = recording;
        recording = null;
        armed = false;
        if (r == null) return;
        try {
            r.finish();
            TASPlaybackFabric.LOGGER.info("Saved recording {}", r.target());
        } catch (IOException e) {
            TASPlaybackFabric.LOGGER.error("Saving recording failed", e);
        }
    }

    /** Called when the game shuts down. Blocks until the recording is on disk. */
    public void onShutdown() {
        RecordingSession r = recording;
        recording = null;
        if (r != null) {
            try { r.finish(); } catch (IOException e) { TASPlaybackFabric.LOGGER.error("Saving recording on exit failed", e); }
        }
    }

    private void message(String s) {
        if (mc.gui != null) mc.gui.setOverlayMessage(Component.literal(s), false);
        TASPlaybackFabric.LOGGER.info(s);
    }

    // ------------------------------------------------------------------ replay

    public void startReplay(Path file, ReplaySession.Mode mode) {
        if (recording != null) stopRecording();
        try {
            DebugText.reset();
            replay = new ReplaySession(mc, file, mode);
            // tutorial hints belong to the viewer, not to the recording
            savedTutorial = mc.options.tutorialStep;
            mc.options.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
            for (String p : replay.problems) TASPlaybackFabric.LOGGER.warn("Recording problem: {}", p);
            TASPlaybackFabric.LOGGER.info("Replaying {} ({} frames, {} s)", file, replay.frameCount(), replay.clock.duration());
            mc.setScreen(null);
        } catch (IOException | RuntimeException e) {
            TASPlaybackFabric.LOGGER.error("Could not open replay {}", file, e);
            replay = null;
        }
    }

    public void stopReplay() {
        ReplaySession r = replay;
        if (r == null) return;
        replay = null;
        r.close();
        if (savedTutorial != null) { mc.options.tutorialStep = savedTutorial; savedTutorial = null; }
        if (mc.level != null) mc.disconnectFromWorld(Component.literal("Replay finished"));
        writeVerifyReport(r);
    }

    /** Replaces the timer during replay. Returns the number of vanilla ticks to run (always 0: we tick ourselves). */
    public int replayAdvance() {
        ReplaySession r = replay;
        if (r.hadLevel && mc.level == null) { // the user left the replay world (pause menu -> disconnect)
            stopReplay();
            return 0;
        }
        try {
            r.advance();
            pollReplayKeys(r);
        } catch (RuntimeException e) {
            TASPlaybackFabric.LOGGER.error("Replay failed", e);
            stopReplay();
        }
        return 0;
    }

    private void pollReplayKeys(ReplaySession r) {
        // Viewer input never reaches the replayed screens (it is blocked), so the controls work with screens open too.
        if (pressed(GLFW.GLFW_KEY_ESCAPE)) { stopReplay(); return; }
        if (pressed(GLFW.GLFW_KEY_SPACE)) r.togglePause();
        if (pressed(GLFW.GLFW_KEY_RIGHT)) r.step(1);
        if (pressed(GLFW.GLFW_KEY_LEFT)) r.step(-1);
        if (pressed(GLFW.GLFW_KEY_UP)) r.speed = Math.min(16, r.speed * 2);
        if (pressed(GLFW.GLFW_KEY_DOWN)) r.speed = Math.max(1.0 / 16, r.speed / 2);
    }

    private void afterReplayFrame() {
        ReplaySession r = replay;
        if (r.mode == ReplaySession.Mode.STEP_FRAMES && r.current != null) {
            final HudEvents.Thumbnail expected = r.recordedThumbnails.get(r.current.key.frame);
            if (expected != null) {
                final long frame = r.current.key.frame;
                Screenshot.takeScreenshot(mc.getMainRenderTarget(), img -> {
                    try {
                        double d = RecordingSession.thumbnail(frame, img).meanAbsDiff(expected);
                        saveDebugImage(img, "rep", frame);
                        verifyDiffs.add(d);
                        TASPlaybackFabric.LOGGER.info("Verify frame {}: mean abs diff {}", frame, String.format("%.2f", d));
                    } finally {
                        img.close();
                    }
                });
            }
        }
        if (r.finished && Boolean.getBoolean("tasplayback.exitAfterReplay")) {
            stopReplay();
            mc.stop();
        }
    }

    private void writeVerifyReport(ReplaySession r) {
        if (verifyDiffs.isEmpty()) return;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("recording", r.file.getFileName().toString());
        m.put("comparedFrames", (long) verifyDiffs.size());
        double max = 0, sum = 0;
        for (double d : verifyDiffs) { max = Math.max(max, d); sum += d; }
        m.put("meanAbsDiffAverage", sum / verifyDiffs.size());
        m.put("meanAbsDiffMax", max);
        m.put("diffs", new ArrayList<Object>(verifyDiffs));
        try {
            Path out = mc.gameDirectory.toPath().resolve("tasplayback").resolve("verify-report.json");
            Files.write(out, Json.write(m).getBytes(StandardCharsets.UTF_8));
            TASPlaybackFabric.LOGGER.info("Wrote {}", out);
        } catch (IOException e) {
            TASPlaybackFabric.LOGGER.error("Could not write verify report", e);
        }
        verifyDiffs.clear();
    }

    private void maybeAutoplay() {
        if (autoplayDone) return;
        String auto = System.getProperty("tasplayback.autoplay");
        if (auto == null || mc.getOverlay() != null) return;
        if (DevTest.active() && !(DevTest.done() && mc.level == null && mc.screen != null && mc.getConnection() == null)) return;
        autoplayDone = true;
        try {
            Path p = auto.equals("latest") ? latestRecording() : Path.of(auto);
            if (p != null) startReplay(p, Boolean.getBoolean("tasplayback.stepFrames") ? ReplaySession.Mode.STEP_FRAMES : ReplaySession.Mode.REALTIME);
        } catch (IOException e) {
            TASPlaybackFabric.LOGGER.error("autoplay failed", e);
        }
    }

    public Path latestRecording() throws IOException {
        Path best = null;
        try (java.util.stream.Stream<Path> s = Files.list(recordingsDir())) {
            for (Path p : (Iterable<Path>) s::iterator) {
                if (!p.toString().endsWith(".tasp")) continue;
                if (best == null || Files.getLastModifiedTime(p).compareTo(Files.getLastModifiedTime(best)) > 0) best = p;
            }
        }
        return best;
    }

    /** With -Dtasplayback.debugImages=N, saves every N-th verification frame as a full PNG for inspection. */
    static void saveDebugImage(com.mojang.blaze3d.platform.NativeImage img, String prefix, long frame) {
        int every = Integer.getInteger("tasplayback.debugImages", 0);
        if (every <= 0 || frame % every != 0) return;
        try {
            Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("tasplayback").resolve("verify");
            Files.createDirectories(dir);
            img.writeToFile(dir.resolve(prefix + "-" + frame + ".png"));
        } catch (IOException e) {
            TASPlaybackFabric.LOGGER.warn("could not save debug image", e);
        }
    }

    public String replayStatus() { return replay == null ? null : replay.status(); }
    public boolean recordingIndicator() { return recording != null || armed; }
    public boolean isArmedOnly() { return recording == null && armed; }
}
