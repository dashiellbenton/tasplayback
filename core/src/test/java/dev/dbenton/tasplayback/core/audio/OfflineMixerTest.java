package dev.dbenton.tasplayback.core.audio;

import dev.dbenton.tasplayback.core.time.TimeKey;
import dev.dbenton.tasplayback.core.timeline.PlaybackClock;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OfflineMixerTest {
    static final int RATE = 48000;

    static PcmBuffer impulse(int rate) {
        float[] d = new float[64];
        d[0] = 1f;
        return new PcmBuffer(rate, 1, d);
    }

    static PcmBuffer dc(int frames, float v) {
        float[] d = new float[frames];
        java.util.Arrays.fill(d, v);
        return new PcmBuffer(RATE, 1, d);
    }

    static OfflineMixer mixer() {
        return new OfflineMixer(RATE, ListenerTrack.fixed(ListenerPose.ORIGIN), DistanceModel.MINECRAFT_LINEAR);
    }

    static int firstNonZero(float[] stereo) {
        for (int i = 0; i < stereo.length / 2; i++) if (stereo[2 * i] != 0f || stereo[2 * i + 1] != 0f) return i;
        return -1;
    }

    @Test
    void clickOnFrameNLandsOnSampleNOverFpsTimes48k() {
        for (int fps : new int[] { 24, 30, 60, 144 }) {
            // A slow TAS: recorded at 2 TPS with irregular wall timing; frame N is at output time N/fps.
            List<TimeKey> keys = new ArrayList<>();
            int frames = fps * 3;
            for (int n = 0; n < frames; n++) {
                double ticks = 1000 + n * 20.0 / fps;
                long tick = (long) Math.floor(ticks);
                keys.add(new TimeKey(0, tick, (float) (ticks - tick), n));
            }
            PlaybackClock clock = PlaybackClock.realTime(keys);
            for (int n : new int[] { 0, 1, 7, fps, frames - 1 }) {
                OfflineMixer m = mixer();
                long start = OfflineMixer.toSample(clock.timeOf(keys.get(n)), RATE);
                long expected = Math.round(n / (double) fps * RATE);
                assertTrue(Math.abs(start - expected) <= 1, "fps " + fps + " frame " + n + ": " + start + " vs " + expected);
                // the float partial tick introduces at most sub-sample error; the mixer itself must be exact:
                ScheduledSound s = new ScheduledSound(impulse(RATE), start).at(0, 0, 0, true);
                m.add(s);
                float[] out = m.render((int) start + 200);
                assertEquals(start, firstNonZero(out), "fps " + fps + " frame " + n);
            }
        }
    }

    @Test
    void sampleAccurateStartIndependentOfBlockAlignmentAndSourceRate() {
        for (long start : new long[] { 0, 1, 63, 64, 65, 1000, 4799 }) {
            for (int srcRate : new int[] { 48000, 44100, 22050 }) {
                OfflineMixer m = mixer();
                m.add(new ScheduledSound(impulse(srcRate), start).at(0, 0, 0, true));
                float[] out = m.render(5000);
                assertEquals(start, firstNonZero(out), "start " + start + " rate " + srcRate);
            }
        }
    }

    @Test
    void renderingInPiecesEqualsRenderingAtOnce() {
        OfflineMixer a = mixer(), b = mixer();
        for (OfflineMixer m : new OfflineMixer[] { a, b }) {
            ScheduledSound s = new ScheduledSound(dc(3000, 0.5f), 100).at(3, 0, 4, false);
            s.pitch = 1.37f;
            m.add(s);
            m.add(new ScheduledSound(impulse(RATE), 777).at(0, 0, 0, true));
        }
        float[] whole = a.render(4000);
        float[] parts = new float[8000];
        int pos = 0;
        for (int n : new int[] { 1, 63, 500, 1436, 2000 }) {
            float[] p = b.render(n);
            System.arraycopy(p, 0, parts, pos * 2, n * 2);
            pos += n;
        }
        assertArrayEquals(whole, parts);
    }

    @Test
    void panningFollowsTheListenersRightHandSide() {
        // yaw 0 faces +Z (south); OpenAL right = forward x up = -X (west)
        double[] r = ListenerPose.ORIGIN.right();
        assertEquals(-1, r[0], 1e-9);
        float[] west = renderDc(-5, 0, 0, ListenerPose.ORIGIN);
        assertTrue(west[1] > west[0] * 2, "source on the right is louder on the right");
        float[] east = renderDc(5, 0, 0, ListenerPose.ORIGIN);
        assertTrue(east[0] > east[1] * 2);
        float[] ahead = renderDc(0, 0, 5, ListenerPose.ORIGIN);
        assertEquals(ahead[0], ahead[1], 1e-6);
        // turn to face east (yaw -90): a source to the east is now straight ahead
        float[] turned = renderDc(5, 0, 0, new ListenerPose(0, 0, 0, -90f, 0f));
        assertEquals(turned[0], turned[1], 1e-5);
    }

    private static float[] renderDc(double x, double y, double z, ListenerPose pose) {
        OfflineMixer m = new OfflineMixer(RATE, ListenerTrack.fixed(pose), DistanceModel.MINECRAFT_LINEAR);
        m.add(new ScheduledSound(dc(1000, 1f), 0).at(x, y, z, false));
        float[] out = m.render(10);
        return new float[] { out[2 * 5], out[2 * 5 + 1] };
    }

    @Test
    void linearDistanceAttenuationAndConstantPower() {
        float[] half = renderDc(8, 0, 0, ListenerPose.ORIGIN); // max distance 16 -> gain 0.5
        assertEquals(0.25, half[0] * half[0] + half[1] * half[1], 1e-6);
        float[] far = renderDc(0, 0, 16, ListenerPose.ORIGIN);
        assertEquals(0f, far[0]); assertEquals(0f, far[1]);
        float[] beyond = renderDc(0, 40, 0, ListenerPose.ORIGIN);
        assertEquals(0f, beyond[0]);
        float[] centered = renderDc(0, 0, 0, ListenerPose.ORIGIN);
        assertEquals(Math.sqrt(0.5), centered[0], 1e-6);
        assertEquals(Math.sqrt(0.5), centered[1], 1e-6);
        assertEquals(1.0, new DistanceModel(1, 1, true).gain(0.2, 10), 0);
    }

    @Test
    void stereoSourcesPlayUnspatialized() {
        OfflineMixer m = mixer();
        float[] d = new float[200];
        for (int i = 0; i < 100; i++) { d[2 * i] = 0.3f; d[2 * i + 1] = -0.6f; }
        ScheduledSound s = new ScheduledSound(new PcmBuffer(RATE, 2, d), 0).at(-100, 0, 0, false);
        s.volume = 0.5f;
        m.add(s);
        float[] out = m.render(10);
        assertEquals(0.15f, out[10], 1e-6);
        assertEquals(-0.3f, out[11], 1e-6);
    }

    @Test
    void pitchStopLoopingAndUpdates() {
        // pitch 2 plays a 1000-frame source in 500 output frames
        OfflineMixer m = mixer();
        ScheduledSound fast = new ScheduledSound(dc(1000, 1f), 0).at(0, 0, 0, true);
        fast.pitch = 2f;
        m.add(fast);
        float[] out = m.render(1000);
        assertTrue(out[2 * 498] != 0f);
        assertEquals(0f, out[2 * 501]);

        OfflineMixer s = mixer();
        ScheduledSound stopped = new ScheduledSound(dc(1000, 1f), 10).at(0, 0, 0, true);
        stopped.stopSample = 100;
        s.add(stopped);
        float[] so = s.render(200);
        assertTrue(so[2 * 99] != 0f);
        assertEquals(0f, so[2 * 100]);

        OfflineMixer l = mixer();
        ScheduledSound loop = new ScheduledSound(dc(100, 1f), 0).at(0, 0, 0, true);
        loop.looping = true;
        l.add(loop);
        float[] lo = l.render(1000);
        assertTrue(lo[2 * 999] != 0f);

        OfflineMixer u = mixer();
        ScheduledSound moved = new ScheduledSound(dc(10000, 1f), 0).at(0, 0, 0, true);
        moved.addUpdate(new ScheduledSound.Update(640, 0f, 1f, 0, 0, 0));
        u.add(moved);
        float[] uo = u.render(1000);
        assertTrue(uo[2 * 500] != 0f);
        assertEquals(0f, uo[2 * 704], "volume ramps to 0 within one block after the update");
    }

    @Test
    void cannotScheduleInThePast() {
        OfflineMixer m = mixer();
        m.render(100);
        assertThrows(IllegalStateException.class, () -> m.add(new ScheduledSound(impulse(RATE), 50)));
    }
}
