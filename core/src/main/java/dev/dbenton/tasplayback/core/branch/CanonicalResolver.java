package dev.dbenton.tasplayback.core.branch;

import dev.dbenton.tasplayback.core.time.GameTime;
import dev.dbenton.tasplayback.core.time.TimeKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Resolves the canonical timeline: the final run with every rewound branch discarded (PLAN.md §4.4).
 *
 * <p>Starting from the final segment, walk up the parent links. Each segment contributes its frames
 * from its own start up to the point where its successor on the path branched off ({@code link}).
 * If a parent started at or after that link point, it contributed nothing to the final state and the walk
 * continues to its parent with the same limit. A segment marked {@link Segment#ignored} (a false-positive
 * rewind) keeps all its parent's frames before the child's first frame, i.e. it is treated as a plain continuation.
 */
public final class CanonicalResolver {
    private CanonicalResolver() {}

    public static Resolution resolve(SegmentGraph graph) {
        return resolve(graph, graph.finalSegment());
    }

    public static Resolution resolve(SegmentGraph graph, int finalSegment) {
        List<KeptRange> reversed = new ArrayList<>();
        Segment cur = graph.get(finalSegment);
        GameTime until = null; // unbounded
        int guard = 0;
        while (true) {
            if (++guard > graph.size() + 1) throw new IllegalStateException("cycle in segment graph");
            if (until == null || cur.start.compareTo(until) < 0) {
                reversed.add(new KeptRange(cur.id, cur.start, until));
            }
            if (cur.isRoot()) break;
            GameTime limit = cur.ignored ? cur.start : cur.link;
            // The parent's contribution can never extend past where this segment's own frames begin.
            if (until != null && limit.compareTo(until) > 0) limit = until;
            until = limit;
            cur = graph.get(cur.parent);
        }
        Collections.reverse(reversed);
        return new Resolution(finalSegment, reversed, graph.size());
    }

    /** Result of resolution: ordered kept ranges (oldest first). */
    public static final class Resolution {
        public final int finalSegment;
        public final List<KeptRange> ranges;
        private final boolean[] onPath;

        Resolution(int finalSegment, List<KeptRange> ranges, int segmentCount) {
            this.finalSegment = finalSegment;
            this.ranges = Collections.unmodifiableList(ranges);
            this.onPath = new boolean[segmentCount];
            for (KeptRange r : ranges) onPath[r.segment] = true;
        }

        public boolean isKept(TimeKey k) {
            for (KeptRange r : ranges) if (r.contains(k)) return true;
            return false;
        }

        /** Index into {@link #ranges} that keeps {@code k}, or -1 if discarded. */
        public int rangeIndexOf(TimeKey k) {
            for (int i = 0; i < ranges.size(); i++) if (ranges.get(i).contains(k)) return i;
            return -1;
        }

        /** Segments that contribute no frame at all (fully discarded branches). */
        public List<Integer> fullyDiscardedSegments() {
            List<Integer> out = new ArrayList<>();
            for (int i = 0; i < onPath.length; i++) if (!onPath[i]) out.add(i);
            return out;
        }

        /**
         * Filters records into canonical order: grouped by kept range (oldest first), then recording order
         * within a range. Records outside every range are dropped.
         */
        public <T> List<T> filter(List<T> records, java.util.function.Function<T, TimeKey> keyOf) {
            List<List<T>> buckets = new ArrayList<>();
            for (int i = 0; i < ranges.size(); i++) buckets.add(new ArrayList<T>());
            for (T r : records) {
                int idx = rangeIndexOf(keyOf.apply(r));
                if (idx >= 0) buckets.get(idx).add(r);
            }
            List<T> out = new ArrayList<>();
            for (List<T> b : buckets) {
                Collections.sort(b, (a, c) -> TimeKey.RECORDING_ORDER.compare(keyOf.apply(a), keyOf.apply(c)));
                out.addAll(b);
            }
            return out;
        }
    }
}
