package dev.dbenton.tasplayback.core.timeline;

/**
 * What to do with consecutive frames that share the same game time (tickrate 0, {@code /tick freeze},
 * a paused game). Playback speed applies to game time, so these frames have zero length by nature.
 */
public final class FrozenSpanPolicy {
    public enum Mode {
        /** Skip frozen spans entirely (default; what a TAS video wants). */
        COLLAPSE,
        /** Hold each frozen frame for its recorded wall-clock duration. */
        HOLD_WALL,
        /** Hold each frozen frame for a fixed number of seconds. */
        HOLD_FIXED
    }

    public static final FrozenSpanPolicy COLLAPSE = new FrozenSpanPolicy(Mode.COLLAPSE, 0, 0);

    public final Mode mode;
    /** HOLD_FIXED: seconds per frozen frame. */
    public final double fixedSeconds;
    /** HOLD_WALL: upper bound per frame so an AFK pause doesn't become an hour of video (seconds, 0 = none). */
    public final double maxWallSeconds;

    private FrozenSpanPolicy(Mode mode, double fixedSeconds, double maxWallSeconds) {
        this.mode = mode;
        this.fixedSeconds = fixedSeconds;
        this.maxWallSeconds = maxWallSeconds;
    }

    public static FrozenSpanPolicy holdWall(double maxSecondsPerFrame) {
        if (maxSecondsPerFrame < 0) throw new IllegalArgumentException("max must be >= 0");
        return new FrozenSpanPolicy(Mode.HOLD_WALL, 0, maxSecondsPerFrame);
    }

    public static FrozenSpanPolicy holdFixed(double secondsPerFrame) {
        if (!(secondsPerFrame >= 0)) throw new IllegalArgumentException("seconds must be >= 0");
        return new FrozenSpanPolicy(Mode.HOLD_FIXED, secondsPerFrame, 0);
    }
}
