package dev.dbenton.tasplayback.core.branch;

import dev.dbenton.tasplayback.core.io.FormatException;
import dev.dbenton.tasplayback.core.time.GameTime;
import dev.dbenton.tasplayback.core.time.TimeKey;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CanonicalResolverTest {
    static GameTime t(long tick) { return new GameTime(tick, 0f); }

    /** Frames of one segment, one per tick in [from, to). Frame counters continue from {@code frame0}. */
    static List<TimeKey> frames(int seg, long from, long to, long frame0) {
        List<TimeKey> l = new ArrayList<>();
        for (long k = from; k < to; k++) l.add(new TimeKey(seg, k, 0f, frame0 + (k - from)));
        return l;
    }

    static String describe(CanonicalResolver.Resolution r) { return r.ranges.toString(); }

    @Test
    void singleSegmentKeepsEverything() {
        SegmentGraph g = new SegmentGraph();
        g.add(Segment.root(0, t(0), 0));
        CanonicalResolver.Resolution r = CanonicalResolver.resolve(g);
        assertEquals("[seg 0 [0+0.0, end)]", describe(r));
        assertTrue(r.isKept(new TimeKey(0, 1_000_000, 0.5f, 99)));
        assertTrue(r.fullyDiscardedSegments().isEmpty());
    }

    @Test
    void simpleRewindDropsTheRewoundPart() {
        // A plays 0..500, savestate from tick 300 is loaded -> B continues from 300
        SegmentGraph g = new SegmentGraph();
        g.add(Segment.root(0, t(0), 0));
        g.add(Segment.rewind(1, t(300), 500, 0, t(300), "savestate-load", "tasmod"));
        CanonicalResolver.Resolution r = CanonicalResolver.resolve(g);
        assertEquals("[seg 0 [0+0.0, 300+0.0), seg 1 [300+0.0, end)]", describe(r));
        assertTrue(r.isKept(new TimeKey(0, 299, 0.99f, 299)));
        assertFalse(r.isKept(new TimeKey(0, 300, 0f, 300)), "parent frame at the link point belongs to the child");
        assertFalse(r.isKept(new TimeKey(0, 450, 0f, 450)));
        assertTrue(r.isKept(new TimeKey(1, 300, 0f, 500)));

        List<TimeKey> all = new ArrayList<>(frames(0, 0, 500, 0));
        all.addAll(frames(1, 300, 700, 500));
        List<TimeKey> canon = r.filter(all, k -> k);
        assertEquals(300 + 400, canon.size());
        for (int i = 0; i < canon.size(); i++) assertEquals(i, canon.get(i).tick, "continuous game time");
    }

    @Test
    void rewindPastTheStartOfTheCurrentSegmentWalksUpToTheAncestor() {
        // A 0..500; B from 300 (state saved in A); C loads a state saved in A at 250 while B is live.
        // Detection only knows B was live -> parent B, link 250. B began at 300 > 250, so B contributes nothing.
        SegmentGraph g = new SegmentGraph();
        g.add(Segment.root(0, t(0), 0));
        g.add(Segment.rewind(1, t(300), 500, 0, t(300), "savestate-load", "tasmod"));
        g.add(Segment.rewind(2, t(250), 800, 1, t(250), "savestate-load", "tasmod"));
        CanonicalResolver.Resolution r = CanonicalResolver.resolve(g);
        assertEquals("[seg 0 [0+0.0, 250+0.0), seg 2 [250+0.0, end)]", describe(r));
        assertEquals(Arrays.asList(1), r.fullyDiscardedSegments());
    }

    @Test
    void nestedRewindIntoTheCurrentSegment() {
        SegmentGraph g = new SegmentGraph();
        g.add(Segment.root(0, t(0), 0));
        g.add(Segment.rewind(1, t(300), 500, 0, t(300), "savestate-load", "tasmod"));
        g.add(Segment.rewind(2, t(400), 900, 1, t(400), "savestate-load", "tasmod"));
        assertEquals("[seg 0 [0+0.0, 300+0.0), seg 1 [300+0.0, 400+0.0), seg 2 [400+0.0, end)]", describe(CanonicalResolver.resolve(g)));
    }

    @Test
    void rewindToAStateSavedInAnAlreadyDiscardedBranch() {
        // A 0..; B from 300; C rewinds further back to 200 (B now discarded);
        // D loads a state that had been saved in B at 350 -> B is back on the canonical path, C is discarded.
        SegmentGraph g = new SegmentGraph();
        g.add(Segment.root(0, t(0), 0));
        g.add(Segment.rewind(1, t(300), 500, 0, t(300), "savestate-load", "tasmod"));
        g.add(Segment.rewind(2, t(200), 700, 0, t(200), "savestate-load", "tasmod"));
        g.add(Segment.rewind(3, t(350), 900, 1, t(350), "savestate-load", "tasmod"));
        CanonicalResolver.Resolution r = CanonicalResolver.resolve(g);
        assertEquals("[seg 0 [0+0.0, 300+0.0), seg 1 [300+0.0, 350+0.0), seg 3 [350+0.0, end)]", describe(r));
        assertEquals(Arrays.asList(2), r.fullyDiscardedSegments());
    }

    @Test
    void userCanPromoteADiscardedBranchAndIgnoreAFalsePositive() {
        SegmentGraph g = new SegmentGraph();
        g.add(Segment.root(0, t(0), 0));
        g.add(Segment.rewind(1, t(300), 500, 0, t(300), "savestate-load", "tasmod"));
        g.add(Segment.rewind(2, t(200), 700, 0, t(200), "world-reload", "vanilla"));
        g.setFinalOverride(1);
        assertEquals("[seg 0 [0+0.0, 300+0.0), seg 1 [300+0.0, end)]", describe(CanonicalResolver.resolve(g)));

        g.setFinalOverride(-1);
        assertEquals("[seg 0 [0+0.0, 200+0.0), seg 2 [200+0.0, end)]", describe(CanonicalResolver.resolve(g)));

        // detection attributed a state loaded at 320 to A, but it was saved in B: the user re-links it
        SegmentGraph k = new SegmentGraph();
        k.add(Segment.root(0, t(0), 0));
        k.add(Segment.rewind(1, t(300), 500, 0, t(300), "savestate-load", "tasmod"));
        k.add(Segment.rewind(2, t(320), 800, 0, t(320), "savestate-load", "heuristic"));
        assertEquals("[seg 0 [0+0.0, 320+0.0), seg 2 [320+0.0, end)]", describe(CanonicalResolver.resolve(k)));
        k.replace(k.get(2).withLink(1, t(320)));
        assertEquals("[seg 0 [0+0.0, 300+0.0), seg 1 [300+0.0, 320+0.0), seg 2 [320+0.0, end)]", describe(CanonicalResolver.resolve(k)));

        // or mark the boundary as not-a-rewind: keep the parent up to the child's first frame
        SegmentGraph h = new SegmentGraph();
        h.add(Segment.root(0, t(0), 0));
        h.add(Segment.rewind(1, t(600), 600, 0, t(150), "world-reload", "vanilla").withIgnored(true));
        assertEquals("[seg 0 [0+0.0, 600+0.0), seg 1 [600+0.0, end)]", describe(CanonicalResolver.resolve(h)));
    }

    @Test
    void partialTicksAtTheLinkPoint() {
        SegmentGraph g = new SegmentGraph();
        g.add(Segment.root(0, t(0), 0));
        g.add(Segment.rewind(1, new GameTime(300, 0.5f), 500, 0, new GameTime(300, 0.5f), "manual-rewind", "manual"));
        CanonicalResolver.Resolution r = CanonicalResolver.resolve(g);
        assertTrue(r.isKept(new TimeKey(0, 300, 0.49f, 300)));
        assertFalse(r.isKept(new TimeKey(0, 300, 0.5f, 301)));
        assertFalse(r.isKept(new TimeKey(1, 300, 0.4f, 600)), "child frames before its own start are not part of it");
    }

    @Test
    void jsonRoundTripAndValidation() {
        SegmentGraph g = new SegmentGraph();
        g.add(Segment.root(0, new GameTime(5, 0.25f), 0));
        g.add(Segment.rewind(1, t(300), 500, 0, t(300), "savestate-load", "lotas"));
        g.add(Segment.rewind(2, t(310), 520, 1, t(310), "world-reload", "vanilla").withIgnored(true));
        g.setFinalOverride(1);
        SegmentGraph b = SegmentGraph.fromJson(g.toJsonString());
        assertEquals(g.toJsonString(), b.toJsonString());
        assertEquals(1, b.finalSegment());
        assertTrue(b.get(2).ignored);
        assertEquals(0.25f, b.get(0).start.partial);

        assertThrows(IllegalArgumentException.class, () -> Segment.rewind(1, t(0), 0, 1, t(0), "x", "y"));
        assertThrows(IllegalArgumentException.class, () -> new SegmentGraph().add(Segment.root(3, t(0), 0)));
        assertThrows(FormatException.class, () -> SegmentGraph.fromJson("{\"formatVersion\": 99, \"segments\": []}"));
        assertThrows(FormatException.class, () -> SegmentGraph.fromJson(
                "{\"formatVersion\": 1, \"segments\": [{\"id\": 0, \"startTick\": 0, \"parent\": 0, \"linkTick\": 0}]}"));
    }
}
