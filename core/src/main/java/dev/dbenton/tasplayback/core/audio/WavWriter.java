package dev.dbenton.tasplayback.core.audio;

import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;

/**
 * Streaming RIFF/WAVE writer for stereo output. Sizes are patched on close. Supports 16-bit PCM
 * (clipped, rounded) and 32-bit IEEE float. Plain RIFF limits files to 4 GiB (about 3h of 48 kHz stereo float).
 */
public final class WavWriter implements Closeable {
    public enum Format { PCM16, FLOAT32 }

    private final RandomAccessFile file;
    private final int channels;
    private final Format format;
    private long dataBytes;
    private ByteBuffer buf = ByteBuffer.allocate(0).order(ByteOrder.LITTLE_ENDIAN);

    public WavWriter(Path path, int sampleRate, int channels, Format format) throws IOException {
        this.channels = channels;
        this.format = format;
        this.file = new RandomAccessFile(path.toFile(), "rw");
        file.setLength(0);
        int bytesPerSample = format == Format.PCM16 ? 2 : 4;
        ByteBuffer h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        h.put(new byte[] { 'R', 'I', 'F', 'F' }).putInt(0).put(new byte[] { 'W', 'A', 'V', 'E' });
        h.put(new byte[] { 'f', 'm', 't', ' ' }).putInt(16);
        h.putShort((short) (format == Format.PCM16 ? 1 : 3)).putShort((short) channels).putInt(sampleRate);
        h.putInt(sampleRate * channels * bytesPerSample).putShort((short) (channels * bytesPerSample)).putShort((short) (bytesPerSample * 8));
        h.put(new byte[] { 'd', 'a', 't', 'a' }).putInt(0);
        file.write(h.array());
    }

    /** Writes {@code frames} interleaved frames from {@code samples}. */
    public void write(float[] samples, int frames) throws IOException {
        int n = frames * channels;
        int bytes = n * (format == Format.PCM16 ? 2 : 4);
        if (buf.capacity() < bytes) buf = ByteBuffer.allocate(bytes).order(ByteOrder.LITTLE_ENDIAN);
        buf.clear();
        for (int i = 0; i < n; i++) {
            float s = samples[i];
            if (format == Format.PCM16) {
                float c = s > 1f ? 1f : (s < -1f ? -1f : s);
                buf.putShort((short) Math.round(c * 32767f));
            } else {
                buf.putFloat(s);
            }
        }
        file.write(buf.array(), 0, bytes);
        dataBytes += bytes;
        if (dataBytes > 0xFFFFFFFFL - 36) throw new IOException("WAV exceeds 4 GiB");
    }

    @Override public void close() throws IOException {
        try {
            ByteBuffer b = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
            file.seek(4);
            b.putInt((int) (36 + dataBytes));
            file.write(b.array());
            b.clear();
            file.seek(40);
            b.putInt((int) dataBytes);
            file.write(b.array());
        } finally {
            file.close();
        }
    }
}
