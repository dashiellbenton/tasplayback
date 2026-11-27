package dev.dbenton.tasplayback.core.time;

/**
 * A point in game time: a whole tick plus the render partial tick in [0, 1).
 * This is the recording's clock. Wall-clock time is never used for ordering or playback timing.
 */
public final class GameTime implements Comparable<GameTime> {
    public static final GameTime ZERO = new GameTime(0, 0f);

    public final long tick;
    public final float partial;

    public GameTime(long tick, float partial) {
        if (!(partial >= 0f && partial < 1f)) throw new IllegalArgumentException("partial must be in [0,1): " + partial);
        this.tick = tick;
        this.partial = partial;
    }

    /** Game time in (fractional) ticks. */
    public double ticks() { return tick + (double) partial; }

    @Override public int compareTo(GameTime o) {
        int c = Long.compare(tick, o.tick);
        return c != 0 ? c : Float.compare(partial, o.partial);
    }

    @Override public boolean equals(Object o) {
        if (!(o instanceof GameTime)) return false;
        GameTime g = (GameTime) o;
        return tick == g.tick && Float.floatToIntBits(partial) == Float.floatToIntBits(g.partial);
    }

    @Override public int hashCode() { return Long.hashCode(tick) * 31 + Float.floatToIntBits(partial); }

    @Override public String toString() { return tick + "+" + partial; }
}
