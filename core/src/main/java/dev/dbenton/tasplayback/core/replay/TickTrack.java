package dev.dbenton.tasplayback.core.replay;

import dev.dbenton.tasplayback.core.event.EventRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Per-tick HUD records (player state, inventory changes) of one segment, consumed in order as the
 * replay's tick counter advances.
 */
public final class TickTrack {
    private final List<EventRecord> events = new ArrayList<>();
    private int next;

    public TickTrack(List<EventRecord> segmentHudEvents, Predicate<EventRecord> keep) {
        for (EventRecord e : segmentHudEvents) if (keep.test(e)) events.add(e);
    }

    /** All records with {@code key.tick <= tickCount} not returned before, in recording order. */
    public List<EventRecord> upTo(long tickCount) {
        List<EventRecord> out = new ArrayList<>();
        while (next < events.size() && events.get(next).key.tick <= tickCount) out.add(events.get(next++));
        return out;
    }

    public int size() { return events.size(); }
}
