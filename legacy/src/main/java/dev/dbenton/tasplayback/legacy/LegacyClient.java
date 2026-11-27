package dev.dbenton.tasplayback.legacy;

import dev.dbenton.tasplayback.core.json.Json;
import dev.dbenton.tasplayback.core.replay.HudEvents;
import dev.dbenton.tasplayback.legacy.mixin.MinecraftAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.network.Packet;
import net.minecraft.util.Timer;
import net.minecraft.util.text.TextComponentString;
import org.lwjgl.input.Keyboard;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Central state for the 1.12.2 client (Forge and LegacyFabric): at most one recording and one replay.
 * Default keys: F8 toggles recording (it starts with the next world join); in replay Space pauses,
 * Left/Right step, Up/Down change speed.
 */
public final class LegacyClient {
    private static final LegacyClient INSTANCE = new LegacyClient();

    public static LegacyClient get() { return INSTANCE; }

    private final Minecraft mc = Minecraft.getMinecraft();
    private LegacyRecordingSession recording;
    private LegacyReplaySession replay;
    private boolean armed = Boolean.getBoolean("tasplayback.autoRecord");
    private final int thumbnailEvery = Integer.getInteger("tasplayback.verifyEvery", 0);
    private final Map<Integer, Boolean> keyWasDown = new HashMap<>();
    private float lastWorldFov = 70f, lastHandFov = 70f;
    private final List<Double> verifyDiffs = new ArrayList<>();
    private boolean autoplayDone;
    private boolean savedPauseOnLostFocus = true;
    private net.minecraft.client.tutorial.TutorialSteps savedTutorial;
    private final ThreadLocal<int[]> localAction = ThreadLocal.withInitial(() -> new int[1]);

    private LegacyClient() {}

    public Path recordingsDir() throws IOException {
        Path p = TASPlaybackLegacy.glue().gameDir().resolve("tasplayback").resolve("recordings");
        Files.createDirectories(p);
        return p;
    }

    public boolean isReplaying() { return replay != null; }
    public boolean isRecording() { return recording != null; }
    LegacyReplaySession replay() { return replay; }

    // ------------------------------------------------------------------ recording hooks

    public void onLocalConnection() {
        if (replay != null) return;
        if (recording == null && armed) {
            try {
                recording = LegacyRecordingSession.start(mc, recordingsDir(), thumbnailEvery);
                TASPlaybackLegacy.LOGGER.info("Recording to {}", recording.target());
            } catch (IOException e) {
                TASPlaybackLegacy.LOGGER.error("Could not start recording", e);
                armed = false;
                return;
            }
        }
        if (recording != null) recording.onNewConnection();
    }

    public void onTickStart() {
        LegacyDevTest.tick(mc);
        if (recording != null && replay == null) recording.onTickStart(mc);
    }

    public void onTickEnd() {
        if (recording != null && replay == null) recording.onTickEnd(mc);
        if (replay == null) pollRecordingKeys();
        maybeAutoplay();
    }

    public void onMainThreadPacket(Packet<?> packet) {
        if (recording != null && replay == null) {
            recording.onMainThreadPacket(packet, ((MinecraftAccessor) mc).tasp$timer().renderPartialTicks);
        }
    }

    public void beginLocalAction() { localAction.get()[0]++; }
    public void endLocalAction() { localAction.get()[0]--; }

    public void onClientBlockChange(net.minecraft.util.math.BlockPos pos, net.minecraft.block.state.IBlockState state, int flags) {
        if (recording != null && replay == null && localAction.get()[0] > 0) recording.onLocalBlockChange(pos, state, flags);
    }

    public void onFrameRendered(float partial) {
        if (recording != null && replay == null) recording.onFrameRendered(mc, partial, lastWorldFov, lastHandFov);
        if (replay != null) afterReplayFrame();
        LegacyOverlay.render(mc);
    }

    public float fov(boolean world, float vanilla) {
        if (replay != null && replay.current != null) return world ? replay.current.fov : replay.current.handFov;
        if (world) lastWorldFov = vanilla; else lastHandFov = vanilla;
        return vanilla;
    }

    private boolean pressed(int key) {
        boolean down = Keyboard.isKeyDown(key);
        Boolean was = keyWasDown.put(key, down);
        return down && (was == null || !was);
    }

    private void pollRecordingKeys() {
        if (mc.currentScreen != null) return;
        if (pressed(Keyboard.KEY_F8)) {
            if (recording != null) stopRecording();
            else {
                armed = !armed;
                message(armed ? "TASPlayback: recording starts on the next world join (rejoin the world to start now)" : "TASPlayback: recording disarmed");
            }
        }
    }

    private void message(String s) {
        if (mc.ingameGUI != null) mc.ingameGUI.setOverlayMessage(new TextComponentString(s), false);
        TASPlaybackLegacy.LOGGER.info(s);
    }

    public void stopRecording() {
        final LegacyRecordingSession r = recording;
        if (r == null) return;
        recording = null;
        armed = false;
        new Thread(() -> {
            try {
                r.finish();
                TASPlaybackLegacy.LOGGER.info("Saved recording {}", r.target());
            } catch (IOException e) {
                TASPlaybackLegacy.LOGGER.error("Saving recording failed", e);
            }
        }, "TASPlayback-Finish").start();
    }

    void stopRecordingBlocking() {
        LegacyRecordingSession r = recording;
        recording = null;
        armed = false;
        if (r == null) return;
        try {
            r.finish();
            TASPlaybackLegacy.LOGGER.info("Saved recording {}", r.target());
        } catch (IOException e) {
            TASPlaybackLegacy.LOGGER.error("Saving recording failed", e);
        }
    }

    public void onShutdown() { stopRecordingBlocking(); }

    // ------------------------------------------------------------------ replay

    public void startReplay(Path file, LegacyReplaySession.Mode mode) {
        if (recording != null) stopRecording();
        try {
            replay = new LegacyReplaySession(mc, file, mode);
            // the viewer's window focus must not pause or menu the replay; tutorial hints belong to the viewer
            savedPauseOnLostFocus = mc.gameSettings.pauseOnLostFocus;
            mc.gameSettings.pauseOnLostFocus = false;
            savedTutorial = mc.gameSettings.tutorialStep;
            mc.gameSettings.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
            for (String p : replay.problems) TASPlaybackLegacy.LOGGER.warn("Recording problem: {}", p);
            TASPlaybackLegacy.LOGGER.info("Replaying {} ({} frames, {} s)", file, replay.frameCount(), replay.clock.duration());
            mc.displayGuiScreen(null);
        } catch (IOException | RuntimeException e) {
            TASPlaybackLegacy.LOGGER.error("Could not open replay {}", file, e);
            replay = null;
        }
    }

    public void stopReplay() {
        LegacyReplaySession r = replay;
        if (r == null) return;
        replay = null;
        r.close();
        mc.gameSettings.pauseOnLostFocus = savedPauseOnLostFocus;
        if (savedTutorial != null) { mc.gameSettings.tutorialStep = savedTutorial; savedTutorial = null; }
        if (mc.world != null) mc.loadWorld(null);
        mc.displayGuiScreen(new GuiMainMenu());
        writeVerifyReport(r);
    }

    /** Called after the vanilla timer update during replay. */
    public void replayAdvance(Timer timer) {
        LegacyReplaySession r = replay;
        if (r.hadWorld && mc.world == null) { stopReplay(); timer.elapsedTicks = 0; return; }
        try {
            r.advance(timer);
            pollReplayKeys(r);
        } catch (IOException | RuntimeException e) {
            TASPlaybackLegacy.LOGGER.error("Replay failed", e);
            stopReplay();
            timer.elapsedTicks = 0;
        }
    }

    private void pollReplayKeys(LegacyReplaySession r) {
        if (mc.currentScreen != null) return;
        if (pressed(Keyboard.KEY_SPACE)) r.togglePause();
        if (pressed(Keyboard.KEY_RIGHT)) r.step(1);
        if (pressed(Keyboard.KEY_LEFT)) r.step(-1);
        if (pressed(Keyboard.KEY_UP)) r.speed = Math.min(16, r.speed * 2);
        if (pressed(Keyboard.KEY_DOWN)) r.speed = Math.max(1.0 / 16, r.speed / 2);
        if (pressed(Keyboard.KEY_ESCAPE)) stopReplay();
    }

    private void afterReplayFrame() {
        LegacyReplaySession r = replay;
        if (r.mode == LegacyReplaySession.Mode.STEP_FRAMES && r.current != null) {
            HudEvents.Thumbnail expected = r.recordedThumbnails.get(r.current.key.frame);
            if (expected != null) {
                double d = LegacyRecordingSession.thumbnail(mc, r.current.key.frame).meanAbsDiff(expected);
                verifyDiffs.add(d);
                TASPlaybackLegacy.LOGGER.info("Verify frame {}: mean abs diff {}", r.current.key.frame, String.format("%.2f", d));
            }
        }
        if (r.finished && Boolean.getBoolean("tasplayback.exitAfterReplay")) {
            stopReplay();
            mc.shutdown();
        }
    }

    private void writeVerifyReport(LegacyReplaySession r) {
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
            Path out = TASPlaybackLegacy.glue().gameDir().resolve("tasplayback").resolve("verify-report.json");
            Files.write(out, Json.write(m).getBytes(StandardCharsets.UTF_8));
            TASPlaybackLegacy.LOGGER.info("Wrote {}", out);
        } catch (IOException e) {
            TASPlaybackLegacy.LOGGER.error("Could not write verify report", e);
        }
        verifyDiffs.clear();
    }

    static void saveDebugImage(BufferedImage img, Minecraft mc, String prefix, long frame) {
        int every = Integer.getInteger("tasplayback.debugImages", 0);
        if (every <= 0 || frame % every != 0) return;
        try {
            Path dir = TASPlaybackLegacy.glue().gameDir().resolve("tasplayback").resolve("verify");
            Files.createDirectories(dir);
            ImageIO.write(img, "png", dir.resolve(prefix + "-" + frame + ".png").toFile());
        } catch (IOException e) {
            TASPlaybackLegacy.LOGGER.warn("could not save debug image", e);
        }
    }

    private void maybeAutoplay() {
        if (autoplayDone) return;
        String auto = System.getProperty("tasplayback.autoplay");
        if (auto == null) return;
        if (LegacyDevTest.active() && !(LegacyDevTest.done() && mc.world == null && mc.currentScreen != null)) return;
        if (!LegacyDevTest.active() && !(mc.currentScreen instanceof GuiMainMenu)) return;
        autoplayDone = true;
        try {
            Path p = auto.equals("latest") ? latestRecording() : Paths.get(auto);
            if (p != null) startReplay(p, Boolean.getBoolean("tasplayback.stepFrames") ? LegacyReplaySession.Mode.STEP_FRAMES : LegacyReplaySession.Mode.REALTIME);
        } catch (IOException e) {
            TASPlaybackLegacy.LOGGER.error("autoplay failed", e);
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

    boolean recordingIndicator() { return recording != null || armed; }
    boolean isArmedOnly() { return recording == null && armed; }
}
