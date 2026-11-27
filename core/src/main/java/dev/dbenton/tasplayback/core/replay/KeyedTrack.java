package dev.dbenton.tasplayback.core.replay;

import dev.dbenton.tasplayback.core.event.EventRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Events of one segment released in recording order once the replay has reached both their tick and
 * their frame (see {@link SegmentFeed} for the same rule applied to packets). Release stops at the first
 * event that is not due yet, so recording order is never violated.
 */
public final class KeyedTrack {
    private final List<EventRecord> events = new ArrayList<>();
    private int next;

    public KeyedTrack(List<EventRecord> segmentEvents, Predicate<EventRecord> keep) {
        for (EventRecord e : segmentEvents) if (keep.test(e)) events.add(e);
    }

    public List<EventRecord> due(long tickCount, long frame) {
        List<EventRecord> out = new ArrayList<>();
        while (next < events.size()) {
            EventRecord e = events.get(next);
            if (e.key.tick > tickCount || e.key.frame > frame) break;
            out.add(e);
            next++;
        }
        return out;
    }

    public int size() { return events.size(); }
}
