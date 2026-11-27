package dev.dbenton.tasplayback.core.audio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class WavWriterTest {
    @TempDir Path tmp;

    @Test
    void writesValidPcm16AndFloatFiles() throws Exception {
        float[] s = { 0f, 0f, 1f, -1f, 2f, -2f, 0.5f, -0.25f };
        for (WavWriter.Format fmt : WavWriter.Format.values()) {
            Path p = tmp.resolve(fmt + ".wav");
            try (WavWriter w = new WavWriter(p, 48000, 2, fmt)) {
                w.write(s, 2);
                w.write(new float[] { s[4], s[5], s[6], s[7] }, 2);
            }
            ByteBuffer b = ByteBuffer.wrap(Files.readAllBytes(p)).order(ByteOrder.LITTLE_ENDIAN);
            int bps = fmt == WavWriter.Format.PCM16 ? 2 : 4;
            assertEquals(44 + 8 * bps, b.capacity());
            assertEquals(0x46464952, b.getInt(0));            // "RIFF"
            assertEquals(36 + 8 * bps, b.getInt(4));
            assertEquals(fmt == WavWriter.Format.PCM16 ? 1 : 3, b.getShort(20));
            assertEquals(2, b.getShort(22));
            assertEquals(48000, b.getInt(24));
            assertEquals(48000 * 2 * bps, b.getInt(28));
            assertEquals(8 * bps, b.getInt(40));
            if (fmt == WavWriter.Format.PCM16) {
                assertEquals(32767, b.getShort(44 + 4));
                assertEquals(-32767, b.getShort(44 + 6));
                assertEquals(32767, b.getShort(44 + 8), "clipped");
            } else {
                assertEquals(2f, b.getFloat(44 + 16), "float keeps headroom");
            }
        }
        // the JDK can parse the PCM file
        javax.sound.sampled.AudioFormat f = javax.sound.sampled.AudioSystem.getAudioFileFormat(tmp.resolve("PCM16.wav").toFile()).getFormat();
        assertEquals(48000f, f.getSampleRate());
        assertEquals(2, f.getChannels());
    }
}
