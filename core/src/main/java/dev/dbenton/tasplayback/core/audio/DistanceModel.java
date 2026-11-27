package dev.dbenton.tasplayback.core.audio;

/**
 * OpenAL distance attenuation. Minecraft uses the linear model with reference distance 0, rolloff 1 and
 * max distance = the sound's attenuation distance; the exact parameters are confirmed per version from
 * decompiled sources in the version layer (M4), so they are parameters here.
 */
public final class DistanceModel {
    public final double referenceDistance;
    public final double rolloff;
    /** Clamp distance to at least the reference distance (AL_LINEAR_DISTANCE_CLAMPED). */
    public final boolean clamped;

    public DistanceModel(double referenceDistance, double rolloff, boolean clamped) {
        this.referenceDistance = referenceDistance;
        this.rolloff = rolloff;
        this.clamped = clamped;
    }

    public static final DistanceModel MINECRAFT_LINEAR = new DistanceModel(0, 1, false);

    /** OpenAL 1.1 AL_LINEAR_DISTANCE(_CLAMPED) gain for a source at {@code distance} with AL_MAX_DISTANCE {@code max}. */
    public double gain(double distance, double max) {
        double ref = referenceDistance;
        if (max <= ref) return distance <= ref ? 1 : 0;
        double d = Math.min(distance, max);
        if (clamped) d = Math.max(d, ref);
        double g = 1 - rolloff * (d - ref) / (max - ref);
        return g < 0 ? 0 : (g > 1 ? 1 : g);
    }
}
