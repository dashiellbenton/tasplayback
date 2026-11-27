package dev.dbenton.tasplayback.core.branch;

import dev.dbenton.tasplayback.core.time.GameTime;
import dev.dbenton.tasplayback.core.time.TimeKey;

/** Frames of {@code segment} with game time in {@code [from, to)} belong to the canonical timeline. {@code to == null} = unbounded. */
public final class KeptRange {
    public final int segment;
    public final GameTime from;
    public final GameTime to;

    KeptRange(int segment, GameTime from, GameTime to) {
        this.segment = segment;
        this.from = from;
        this.to = to;
    }

    public boolean contains(TimeKey k) {
        return k.segment == segment && k.compareGameTime(from) >= 0 && (to == null || k.compareGameTime(to) < 0);
    }

    @Override public String toString() { return "seg " + segment + " [" + from + ", " + (to == null ? "end" : to.toString()) + ")"; }
}
