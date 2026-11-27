package dev.dbenton.tasplayback.core.timeline;

import dev.dbenton.tasplayback.core.time.TimeKey;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlaybackClockTest {
    static TimeKey k(long tick, float partial, long frame) { return new TimeKey(0, tick, partial, frame); }

    @Test
    void outputTimeFollowsGameTimeNotWallTime() {
        // recorded at 1 TPS with 10 frames per tick and huge wall gaps: output is still 20 TPS real time
        List<TimeKey> keys = new ArrayList<>();
        long[] wall = new long[50];
        for (int i = 0; i < 50; i++) { keys.add(k(i / 10, (i % 10) / 10f, i)); wall[i] = i * 1_000_000_000L * 7; }
        PlaybackClock c = new PlaybackClock(keys, wall, 20, 1, FrozenSpanPolicy.COLLAPSE);
        for (int i = 0; i < 50; i++) assertEquals(keys.get(i).ticks() / 20.0, c.frameTime(i), 1e-12);
        assertEquals(keys.get(49).ticks() / 20, c.duration(), 1e-12); // partial 0.9f is 0.900000095... as a float

        PlaybackClock fast = new PlaybackClock(keys, wall, 20, 2.0, FrozenSpanPolicy.COLLAPSE);
        assertEquals(c.duration() / 2, fast.duration(), 1e-12);
    }

    @Test
    void frozenSpansCollapseByDefault() {
        List<TimeKey> keys = Arrays.asList(k(10, 0f, 0), k(10, 0.5f, 1), k(10, 0.5f, 2), k(10, 0.5f, 3), k(11, 0f, 4));
        long[] wall = { 0, 100_000_000L, 2_100_000_000L, 4_100_000_000L, 4_200_000_000L };
        PlaybackClock c = new PlaybackClock(keys, wall, 20, 1, FrozenSpanPolicy.COLLAPSE);
        assertEquals(c.frameTime(1), c.frameTime(3), 0);
        assertEquals(1.0 / 20, c.duration(), 1e-12);
        // the frame shown at the frozen instant is the last frame of the frozen span
        assertEquals(3, c.frameAt(c.frameTime(1)));
        assertEquals(-1, c.frameAt(-0.001));
    }

    @Test
    void frozenSpansCanBeHeld() {
        List<TimeKey> keys = Arrays.asList(k(10, 0f, 0), k(10, 0.5f, 1), k(10, 0.5f, 2), k(10, 0.5f, 3), k(11, 0f, 4));
        long[] wall = { 0, 100_000_000L, 2_100_000_000L, 4_100_000_000L, 4_200_000_000L };
        PlaybackClock wallClock = new PlaybackClock(keys, wall, 20, 1, FrozenSpanPolicy.holdWall(0));
        assertEquals(0.025 + 4.0 + 0.025, wallClock.duration(), 1e-9);
        PlaybackClock capped = new PlaybackClock(keys, wall, 20, 1, FrozenSpanPolicy.holdWall(0.5));
        assertEquals(0.05 + 1.0, capped.duration(), 1e-9);
        PlaybackClock fixed = new PlaybackClock(keys, wall, 20, 1, FrozenSpanPolicy.holdFixed(0.1));
        assertEquals(0.05 + 0.2, fixed.duration(), 1e-9);
    }

    @Test
    void recordsBetweenFramesLandAtTheirExactGameTime() {
        // frames at 10.9 and 11.1; a sound played during tick 11 carries the next frame's index (1)
        List<TimeKey> keys = Arrays.asList(k(10, 0.9f, 0), k(11, 0.1f, 1), k(11, 0.6f, 2));
        PlaybackClock c = PlaybackClock.realTime(keys);
        double sound = c.timeOf(k(11, 0f, 1));
        assertEquals((11.0 - 10.9) / 20, sound, 1e-6);
        assertEquals(c.frameTime(2), c.timeOf(k(11, 0.6f, 2)), 1e-9);
    }

    @Test
    void backwardsJumpsAddNoTimeAndFramesMustIncrease() {
        List<TimeKey> keys = Arrays.asList(k(10, 0f, 0), k(5, 0f, 1), k(6, 0f, 2));
        PlaybackClock c = PlaybackClock.realTime(keys);
        assertEquals(0, c.frameTime(1), 0);
        assertEquals(0.05, c.frameTime(2), 1e-12);
        assertThrows(IllegalArgumentException.class, () -> PlaybackClock.realTime(Arrays.asList(k(1, 0f, 5), k(2, 0f, 5))));
    }

    @Test
    void alphaBetweenFrames() {
        PlaybackClock c = PlaybackClock.realTime(Arrays.asList(k(0, 0f, 0), k(1, 0f, 1), k(2, 0f, 2)));
        int i = c.frameAt(0.0625);
        assertEquals(1, i);
        assertEquals(0.25, c.alphaAt(i, 0.0625), 1e-12);
        assertEquals(0, c.alphaAt(2, 10), 0);
    }
}
