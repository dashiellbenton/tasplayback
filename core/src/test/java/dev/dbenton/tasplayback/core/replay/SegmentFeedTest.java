package dev.dbenton.tasplayback.core.replay;

import dev.dbenton.tasplayback.core.event.EventRecord;
import dev.dbenton.tasplayback.core.io.FormatException;
import dev.dbenton.tasplayback.core.time.TimeKey;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SegmentFeedTest {
    static TimeKey k(long tick, long frame) { return new TimeKey(0, tick, 0f, frame); }

    static List<EventRecord> script() {
        List<EventRecord> e = new ArrayList<>();
        for (int i = 0; i < 3; i++) e.add(WorldEvents.rawFrame(k(0, 0), i, new byte[] { (byte) i }, 0, 1)); // login, netty-handled
        e.add(WorldEvents.rawFrame(k(0, 0), 3, new byte[] { 3 }, 0, 1));
        e.add(WorldEvents.deliver(k(0, 0), 3));       // handled before the first tick
        e.add(WorldEvents.rawFrame(k(0, 1), 4, new byte[] { 4 }, 0, 1));
        e.add(WorldEvents.rawFrame(k(0, 1), 5, new byte[] { 5 }, 0, 1));
        e.add(WorldEvents.deliver(k(0, 2), 5));       // handled at frame 2, still tick 0
        e.add(WorldEvents.rawFrame(k(1, 3), 6, new byte[] { 6 }, 0, 1));
        e.add(WorldEvents.deliver(k(1, 4), 6));       // after tick 1 ran
        return e;
    }

    static List<Integer> ids(List<byte[]> l) {
        List<Integer> out = new ArrayList<>();
        for (byte[] b : l) out.add((int) b[0]);
        return out;
    }

    @Test
    void feedsFramesInOrderAtTheirDeliverPoints() {
        SegmentFeed f = new SegmentFeed(script(), key -> true);
        assertEquals(7, f.frameCount());
        assertEquals(Arrays.asList(0, 1, 2, 3), ids(f.framesDue(0, 0)), "netty-handled login frames ride along with the first DELIVER");
        assertTrue(f.framesDue(0, 1).isEmpty());
        assertEquals(Arrays.asList(4, 5), ids(f.framesDue(0, 2)));
        assertTrue(f.framesDue(0, 100).isEmpty(), "the next DELIVER happened after tick 1");
        assertEquals(Arrays.asList(6), ids(f.framesDue(1, 100)));
        assertTrue(f.exhausted());
    }

    @Test
    void catchingUpFeedsEverythingDueAtOnce() {
        SegmentFeed f = new SegmentFeed(script(), key -> true);
        assertEquals(Arrays.asList(0, 1, 2, 3, 4, 5, 6), ids(f.framesDue(5, 50)));
    }

    @Test
    void deliveriesAfterTheBranchPointAreDropped() {
        SegmentFeed f = new SegmentFeed(script(), key -> key.tick < 1);
        assertEquals(Arrays.asList(0, 1, 2, 3, 4, 5), ids(f.framesDue(9, 99)));
        assertTrue(f.exhausted());
    }

    @Test
    void detectsGapsAndDanglingDelivers() {
        List<EventRecord> gap = new ArrayList<>();
        gap.add(WorldEvents.rawFrame(k(0, 0), 1, new byte[1], 0, 1));
        assertThrows(FormatException.class, () -> new SegmentFeed(gap, k -> true));
        List<EventRecord> dangling = new ArrayList<>();
        dangling.add(WorldEvents.deliver(k(0, 0), 2));
        SegmentFeed f = new SegmentFeed(dangling, k -> true);
        assertThrows(FormatException.class, () -> f.framesDue(0, 0));
    }

    @Test
    void tickTrackReleasesRecordsPerTick() {
        List<EventRecord> h = new ArrayList<>();
        for (int t = 1; t <= 5; t++) h.add(new EventRecord(k(t, t * 3), HudEvents.PLAYER_TICK, new byte[] { (byte) t }));
        TickTrack tt = new TickTrack(h, e -> e.key.tick != 4);
        assertEquals(0, tt.upTo(0).size());
        assertEquals(2, tt.upTo(2).size());
        assertEquals(2, tt.upTo(5).size());
    }

    @Test
    void thumbnailsRoundTripAndCompare() {
        byte[] rgba = new byte[4 * 4 * 4];
        for (int i = 0; i < 16; i++) { rgba[i * 4] = (byte) 200; rgba[i * 4 + 1] = (byte) (i * 10); rgba[i * 4 + 2] = 0; rgba[i * 4 + 3] = (byte) 255; }
        HudEvents.Thumbnail t = HudEvents.Thumbnail.fromRgba(7, 4, 4, rgba, 2, 2);
        assertEquals(200, t.rgb[0] & 0xFF);
        assertEquals((0 + 10 + 40 + 50) / 4, t.rgb[1] & 0xFF);
        HudEvents.Thumbnail back = HudEvents.Thumbnail.from(t.toRecord(k(1, 7)));
        assertEquals(7, back.frame);
        assertEquals(0.0, t.meanAbsDiff(back));
    }
}
