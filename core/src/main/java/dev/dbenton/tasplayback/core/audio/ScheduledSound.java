package dev.dbenton.tasplayback.core.audio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One sound instance placed on the output timeline, with its parameter changes over time. */
public final class ScheduledSound {
    /** A parameter change taking effect at {@code sample} (from per-tick updates of moving sounds). */
    public static final class Update {
        public final long sample;
        public final float volume, pitch;
        public final double x, y, z;

        public Update(long sample, float volume, float pitch, double x, double y, double z) {
            this.sample = sample; this.volume = volume; this.pitch = pitch; this.x = x; this.y = y; this.z = z;
        }
    }

    public final PcmBuffer pcm;
    public final long startSample;
    /** First sample at which the sound is silenced, or -1 to play until the data ends. */
    public long stopSample = -1;
    public boolean looping;
    public float volume = 1f, pitch = 1f;
    public double x, y, z;
    /** Position is relative to the listener (UI, music). */
    public boolean relative;
    /** False: no distance attenuation (ATTENUATION_NONE). */
    public boolean attenuated = true;
    public float maxDistance = 16f;
    /** Category/master volume multiplier from the recorded options. */
    public float gainMultiplier = 1f;
    final List<Update> updates = new ArrayList<>();

    public ScheduledSound(PcmBuffer pcm, long startSample) {
        if (startSample < 0) throw new IllegalArgumentException("startSample must be >= 0");
        this.pcm = pcm;
        this.startSample = startSample;
    }

    public ScheduledSound at(double x, double y, double z, boolean relative) {
        this.x = x; this.y = y; this.z = z; this.relative = relative; return this;
    }

    public void addUpdate(Update u) {
        updates.add(u);
        Collections.sort(updates, (a, b) -> Long.compare(a.sample, b.sample));
    }
}
