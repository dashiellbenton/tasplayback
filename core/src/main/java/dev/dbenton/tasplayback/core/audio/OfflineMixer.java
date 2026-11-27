package dev.dbenton.tasplayback.core.audio;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Sample-accurate offline mixer for video export (PLAN.md §6.4).
 *
 * <p>Renders the output sequentially in blocks. A sound's first source sample lands exactly on its
 * {@link ScheduledSound#startSample}. Positional (mono) sources get OpenAL-style linear distance
 * attenuation and constant-power stereo panning from the listener's right vector; stereo sources play
 * unspatialized, as OpenAL does. Gains are ramped linearly across each block to avoid zipper noise when
 * a source or the listener moves. Pitch is applied by linear-interpolation resampling.
 *
 * <p>Timing is exact. The mix approximates OpenAL Soft's output; it is not bit-identical to its resampler
 * and panner.
 */
public final class OfflineMixer {
    public static final int DEFAULT_RATE = 48000;
    public static final int BLOCK = 64;
    private static final double[] LISTENER_SPACE_RIGHT = { 1, 0, 0 };

    public final int sampleRate;
    private final ListenerTrack listener;
    private final DistanceModel distance;
    private final List<Voice> pending = new ArrayList<>();
    private final List<Voice> active = new ArrayList<>();
    private long position;

    public OfflineMixer(int sampleRate, ListenerTrack listener, DistanceModel distance) {
        this.sampleRate = sampleRate;
        this.listener = listener;
        this.distance = distance;
    }

    public static long toSample(double seconds, int sampleRate) { return Math.round(seconds * sampleRate); }

    public void add(ScheduledSound s) {
        if (s.startSample < position) throw new IllegalStateException("sound starts before the current render position");
        pending.add(new Voice(s));
        pending.sort(Comparator.comparingLong(v -> v.s.startSample));
    }

    public long position() { return position; }

    /** Renders the next {@code frames} stereo frames into {@code out} (interleaved L,R; overwritten). */
    public void renderNext(float[] out, int frames) {
        if (out.length < frames * 2) throw new IllegalArgumentException("output buffer too small");
        java.util.Arrays.fill(out, 0, frames * 2, 0f);
        int done = 0;
        while (done < frames) {
            int n = Math.min(BLOCK, frames - done);
            long blockStart = position + done;
            long blockEnd = blockStart + n;
            while (!pending.isEmpty() && pending.get(0).s.startSample < blockEnd) active.add(pending.remove(0));
            ListenerPose pose = listener.poseAt(blockStart);
            for (int i = active.size() - 1; i >= 0; i--) {
                Voice v = active.get(i);
                if (!v.render(out, done, blockStart, n, pose)) active.remove(i);
            }
            done += n;
        }
        position += frames;
    }

    /** Convenience: renders {@code frames} frames from the current position into a new array. */
    public float[] render(int frames) {
        float[] out = new float[frames * 2];
        renderNext(out, frames);
        return out;
    }

    private final class Voice {
        final ScheduledSound s;
        double srcPos;
        int nextUpdate;
        float volume, pitch;
        double x, y, z;
        float gainL = Float.NaN, gainR = Float.NaN;

        Voice(ScheduledSound s) {
            this.s = s;
            this.volume = s.volume; this.pitch = s.pitch; this.x = s.x; this.y = s.y; this.z = s.z;
        }

        /** Mixes into out[offset..offset+n) for absolute samples [blockStart, blockStart+n). Returns false when finished. */
        boolean render(float[] out, int offset, long blockStart, int n, ListenerPose pose) {
            long blockEnd = blockStart + n;
            while (nextUpdate < s.updates.size() && s.updates.get(nextUpdate).sample <= blockStart) {
                ScheduledSound.Update u = s.updates.get(nextUpdate++);
                volume = u.volume; pitch = u.pitch; x = u.x; y = u.y; z = u.z;
            }
            long from = Math.max(blockStart, s.startSample);
            long to = blockEnd;
            if (s.stopSample >= 0) to = Math.min(to, s.stopSample);
            if (from >= to) return s.stopSample < 0 || s.stopSample > blockEnd;

            float[] g = gains(pose);
            float startL = Float.isNaN(gainL) ? g[0] : gainL;
            float startR = Float.isNaN(gainR) ? g[1] : gainR;
            gainL = g[0]; gainR = g[1];

            PcmBuffer pcm = s.pcm;
            int srcFrames = pcm.frames();
            double step = pitch * (double) pcm.sampleRate / sampleRate;
            int span = (int) (to - from);
            for (long t = from; t < to; t++) {
                int ip = (int) srcPos;
                if (ip >= srcFrames) {
                    if (!s.looping || srcFrames == 0) return false;
                    srcPos -= srcFrames * Math.floor(srcPos / srcFrames);
                    ip = (int) srcPos;
                }
                double frac = srcPos - ip;
                int ip2 = ip + 1;
                if (ip2 >= srcFrames) ip2 = s.looping ? 0 : -1;
                float k = span <= 1 ? 1f : (float) (t - from) / (span - 1);
                float gl = startL + (gainL - startL) * k;
                float gr = startR + (gainR - startR) * k;
                int o = offset + (int) (t - blockStart);
                if (pcm.channels == 1) {
                    float a = pcm.data[ip];
                    float b = ip2 < 0 ? 0f : pcm.data[ip2];
                    float smp = (float) (a + (b - a) * frac);
                    out[2 * o] += smp * gl;
                    out[2 * o + 1] += smp * gr;
                } else {
                    float al = pcm.data[2 * ip], ar = pcm.data[2 * ip + 1];
                    float bl = ip2 < 0 ? 0f : pcm.data[2 * ip2], br = ip2 < 0 ? 0f : pcm.data[2 * ip2 + 1];
                    out[2 * o] += (float) (al + (bl - al) * frac) * gl;
                    out[2 * o + 1] += (float) (ar + (br - ar) * frac) * gr;
                }
                srcPos += step;
            }
            if (s.stopSample >= 0 && s.stopSample <= blockEnd) return false;
            return s.looping || srcPos < srcFrames;
        }

        float[] gains(ListenerPose pose) {
            float base = Math.max(0f, volume) * s.gainMultiplier;
            if (s.pcm.channels == 2) return new float[] { base, base };
            double rx = s.relative ? x : x - pose.x;
            double ry = s.relative ? y : y - pose.y;
            double rz = s.relative ? z : z - pose.z;
            double dist = Math.sqrt(rx * rx + ry * ry + rz * rz);
            double g = base;
            if (s.attenuated) g *= distance.gain(dist, s.maxDistance);
            double pan = 0;
            if (dist > 1e-9) {
                // AL_SOURCE_RELATIVE positions are in listener space, where OpenAL's right is +X.
                double[] r = s.relative ? LISTENER_SPACE_RIGHT : pose.right();
                pan = (rx * r[0] + ry * r[1] + rz * r[2]) / dist;
                pan = Math.max(-1, Math.min(1, pan));
            }
            float l = (float) (g * Math.sqrt(0.5 * (1 - pan)));
            float rr = (float) (g * Math.sqrt(0.5 * (1 + pan)));
            return new float[] { l, rr };
        }
    }
}
