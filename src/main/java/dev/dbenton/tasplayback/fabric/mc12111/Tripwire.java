package dev.dbenton.tasplayback.fabric.mc12111;

import dev.dbenton.tasplayback.fabric.TASPlaybackFabric;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Test-only guard for hard requirement 1 ("recording must never change the game"): while recorder capture
 * code runs, any draw from a Minecraft random source is counted as a violation. Enabled with
 * {@code -Dtasplayback.tripwire=true}; otherwise every check is a constant {@code false}.
 */
public final class Tripwire {
    public static final boolean ENABLED = Boolean.getBoolean("tasplayback.tripwire");
    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);
    private static final AtomicLong VIOLATIONS = new AtomicLong();

    private Tripwire() {}

    static void enter() { if (ENABLED) DEPTH.get()[0]++; }
    static void exit() { if (ENABLED) DEPTH.get()[0]--; }

    /** Called from the RNG mixins. */
    public static void onRandom() {
        if (!ENABLED || DEPTH.get()[0] == 0) return;
        if (VIOLATIONS.incrementAndGet() <= 5) {
            TASPlaybackFabric.LOGGER.error("TRIPWIRE: recorder capture code consumed randomness", new Throwable());
        }
    }

    static long violations() { return VIOLATIONS.get(); }
}
