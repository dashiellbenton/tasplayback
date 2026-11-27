package dev.dbenton.tasplayback.core.timeline;

import dev.dbenton.tasplayback.core.time.TimeKey;

import java.util.List;

/**
 * Maps the canonical timeline onto output time (seconds from the start of playback/export).
 *
 * <p>Output time advances with game time: {@code Δt = Δticks / ticksPerSecond / speed}, so a run recorded
 * at any tickrate plays back at the target rate (20 TPS by default). Frames with zero game-time delta
 * are handled by the {@link FrozenSpanPolicy}. A backwards game-time jump (only possible through user
 * overrides) contributes zero time.
 */
public final class PlaybackClock {
    public final double ticksPerSecond;
    public final double speed;
    public final FrozenSpanPolicy frozen;

    private final TimeKey[] keys;
    private final double[] out;

    /**
     * @param canonicalFrames frame keys in canonical order (see {@code Resolution.filter})
     * @param wallNanos       wall-clock capture times of those frames (same length), used only by HOLD_WALL
     */
    public PlaybackClock(List<TimeKey> canonicalFrames, long[] wallNanos, double ticksPerSecond, double speed, FrozenSpanPolicy frozen) {
        if (!(ticksPerSecond > 0)) throw new IllegalArgumentException("ticksPerSecond must be > 0");
        if (!(speed > 0)) throw new IllegalArgumentException("speed must be > 0");
        if (wallNanos != null && wallNanos.length != canonicalFrames.size()) throw new IllegalArgumentException("wallNanos length mismatch");
        this.ticksPerSecond = ticksPerSecond;
        this.speed = speed;
        this.frozen = frozen;
        int n = canonicalFrames.size();
        keys = canonicalFrames.toArray(new TimeKey[0]);
        out = new double[n];
        for (int i = 1; i < n; i++) {
            if (keys[i].frame <= keys[i - 1].frame) {
                throw new IllegalArgumentException("canonical frames must have increasing frame indices (at " + i + ")");
            }
            double dg = keys[i].ticks() - keys[i - 1].ticks();
            double dt;
            if (dg > 0) {
                dt = dg / ticksPerSecond / speed;
            } else if (dg == 0) {
                switch (frozen.mode) {
                    case HOLD_WALL:
                        dt = wallNanos == null ? 0 : Math.max(0, (wallNanos[i] - wallNanos[i - 1]) / 1e9);
                        if (frozen.maxWallSeconds > 0) dt = Math.min(dt, frozen.maxWallSeconds);
                        break;
                    case HOLD_FIXED:
                        dt = frozen.fixedSeconds;
                        break;
                    default:
                        dt = 0;
                }
            } else {
                dt = 0;
            }
            out[i] = out[i - 1] + dt;
        }
    }

    public static PlaybackClock realTime(List<TimeKey> canonicalFrames) {
        return new PlaybackClock(canonicalFrames, null, 20.0, 1.0, FrozenSpanPolicy.COLLAPSE);
    }

    public int frameCount() { return keys.length; }
    public TimeKey key(int i) { return keys[i]; }
    public double frameTime(int i) { return out[i]; }
    public double duration() { return keys.length == 0 ? 0 : out[keys.length - 1]; }

    /** Index of the last canonical frame whose frame counter is {@code <= frame}, or -1. */
    public int frameIndexAtOrBefore(long frame) {
        int lo = 0, hi = keys.length - 1, ans = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (keys[mid].frame <= frame) { ans = mid; lo = mid + 1; } else hi = mid - 1;
        }
        return ans;
    }

    /**
     * Output time of an arbitrary record (sound, GUI event) that is on the canonical timeline. Anchored at
     * the canonical frame it was recorded next to, offset by the game-time difference, so a tick-thread
     * sound lands at its exact tick even between frames. Never earlier than 0.
     */
    public double timeOf(TimeKey k) {
        if (keys.length == 0) return 0;
        int i = frameIndexAtOrBefore(k.frame);
        if (i < 0) i = 0;
        double t = out[i] + (k.ticks() - keys[i].ticks()) / ticksPerSecond / speed;
        // keep within the frame's neighbourhood: never before the previous frame, never after the next one
        if (i > 0 && t < out[i - 1]) t = out[i - 1];
        if (i + 1 < keys.length && t > out[i + 1]) t = out[i + 1];
        return Math.max(0, t);
    }

    /**
     * Locates output time {@code t}: returns the index {@code i} of the last frame with {@code frameTime(i) <= t}
     * (the latest frame among equal times, so collapsed frozen spans show their final state), or -1 before the start.
     */
    public int frameAt(double t) {
        int lo = 0, hi = keys.length - 1, ans = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (out[mid] <= t) { ans = mid; lo = mid + 1; } else hi = mid - 1;
        }
        return ans;
    }

    /** Interpolation factor between frame {@code i} and {@code i+1} at output time {@code t}, in [0,1]. */
    public double alphaAt(int i, double t) {
        if (i < 0 || i + 1 >= keys.length) return 0;
        double span = out[i + 1] - out[i];
        if (span <= 0) return 0;
        double a = (t - out[i]) / span;
        return a < 0 ? 0 : (a > 1 ? 1 : a);
    }
}
