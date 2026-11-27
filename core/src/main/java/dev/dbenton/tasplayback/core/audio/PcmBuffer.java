package dev.dbenton.tasplayback.core.audio;

/** Decoded audio: interleaved float samples in [-1, 1], 1 or 2 channels. */
public final class PcmBuffer {
    public final int sampleRate;
    public final int channels;
    public final float[] data;

    public PcmBuffer(int sampleRate, int channels, float[] data) {
        if (sampleRate <= 0) throw new IllegalArgumentException("sampleRate must be > 0");
        if (channels != 1 && channels != 2) throw new IllegalArgumentException("only mono or stereo supported");
        if (data.length % channels != 0) throw new IllegalArgumentException("data length not a multiple of channels");
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.data = data;
    }

    public int frames() { return data.length / channels; }
}
