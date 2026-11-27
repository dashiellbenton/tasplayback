package dev.dbenton.tasplayback.core.time;

import java.util.Comparator;

/**
 * Key attached to every recorded record: {@code (segment, tick, partial, frame)}.
 *
 * <ul>
 *   <li>{@code segment}: recording segment id; a new segment starts at every rewind/savestate load.</li>
 *   <li>{@code tick}, {@code partial}: game time (see {@link GameTime}).</li>
 *   <li>{@code frame}: global render-frame counter, strictly increasing over the whole recording,
 *       even across segments. Records produced between two frames (e.g. tick-thread sounds) carry
 *       the index of the next frame to be rendered. It breaks ties when many frames share one game time
 *       (low tickrate, frozen game).</li>
 * </ul>
 *
 * Two orders matter: {@link #RECORDING_ORDER} (how records were produced) and {@link #GAME_ORDER}
 * (game time within one segment). Ordering across segments is decided by branch resolution, not by ids.
 */
public final class TimeKey {
    public final int segment;
    public final long tick;
    public final float partial;
    public final long frame;

    public TimeKey(int segment, long tick, float partial, long frame) {
        if (!(partial >= 0f && partial < 1f)) throw new IllegalArgumentException("partial must be in [0,1): " + partial);
        this.segment = segment;
        this.tick = tick;
        this.partial = partial;
        this.frame = frame;
    }

    public GameTime gameTime() { return new GameTime(tick, partial); }
    public double ticks() { return tick + (double) partial; }

    /** Compares game time only (tick, partial). */
    public int compareGameTime(GameTime t) {
        int c = Long.compare(tick, t.tick);
        return c != 0 ? c : Float.compare(partial, t.partial);
    }

    /** Production order: frame counter first, then game time. */
    public static final Comparator<TimeKey> RECORDING_ORDER = new Comparator<TimeKey>() {
        @Override public int compare(TimeKey a, TimeKey b) {
            int c = Long.compare(a.frame, b.frame);
            if (c != 0) return c;
            c = Long.compare(a.tick, b.tick);
            return c != 0 ? c : Float.compare(a.partial, b.partial);
        }
    };

    /** Game-time order (tick, partial, frame). Only meaningful within one segment. */
    public static final Comparator<TimeKey> GAME_ORDER = new Comparator<TimeKey>() {
        @Override public int compare(TimeKey a, TimeKey b) {
            int c = Long.compare(a.tick, b.tick);
            if (c != 0) return c;
            c = Float.compare(a.partial, b.partial);
            return c != 0 ? c : Long.compare(a.frame, b.frame);
        }
    };

    @Override public boolean equals(Object o) {
        if (!(o instanceof TimeKey)) return false;
        TimeKey k = (TimeKey) o;
        return segment == k.segment && tick == k.tick && frame == k.frame
                && Float.floatToIntBits(partial) == Float.floatToIntBits(k.partial);
    }

    @Override public int hashCode() {
        int h = segment;
        h = h * 31 + Long.hashCode(tick);
        h = h * 31 + Float.floatToIntBits(partial);
        return h * 31 + Long.hashCode(frame);
    }

    @Override public String toString() { return "[seg " + segment + " @" + tick + "+" + partial + " f" + frame + "]"; }
}
