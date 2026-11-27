package dev.dbenton.tasplayback.core.format;

import dev.dbenton.tasplayback.core.branch.SegmentGraph;
import dev.dbenton.tasplayback.core.event.EventCodec;
import dev.dbenton.tasplayback.core.frame.FrameCodec;
import dev.dbenton.tasplayback.core.io.ByteSource;
import dev.dbenton.tasplayback.core.io.FormatException;
import dev.dbenton.tasplayback.core.sound.SoundEvent;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Damaged or hostile input must only ever surface as {@link FormatException}, never as another runtime error. */
class DecoderFuzzTest {
    interface Decode { void run(byte[] data); }

    private static void fuzz(String what, Decode d) {
        Random r = new Random(what.hashCode());
        for (int i = 0; i < 20000; i++) {
            byte[] b = new byte[r.nextInt(96)];
            r.nextBytes(b);
            if (r.nextBoolean()) for (int k = 0; k < b.length; k++) if (r.nextInt(3) == 0) b[k] = 0;
            try {
                d.run(b);
            } catch (FormatException expected) {
                // fine
            } catch (RuntimeException | OutOfMemoryError e) {
                fail(what + " threw " + e + " on input " + java.util.Arrays.toString(b));
            }
        }
    }

    @Test
    void binaryDecoders() {
        fuzz("frame", b -> { FrameCodec c = new FrameCodec(); c.beginChunk(); ByteSource in = new ByteSource(b); while (in.hasRemaining()) c.decode(in); });
        fuzz("event", b -> { EventCodec c = new EventCodec(); c.beginChunk(); ByteSource in = new ByteSource(b); while (in.hasRemaining()) c.decode(in); });
        fuzz("sound", b -> SoundEvent.decode(1 + (b.length % 4), b));
        fuzz("stream", b -> {
            byte[] withHeader = new byte[ChunkFormat.FILE_HEADER_SIZE + b.length];
            System.arraycopy(ChunkFormat.fileHeader(StreamId.FRAMES, 1), 0, withHeader, 0, ChunkFormat.FILE_HEADER_SIZE);
            System.arraycopy(b, 0, withHeader, ChunkFormat.FILE_HEADER_SIZE, b.length);
            StreamReader.read(withHeader, StreamId.FRAMES, 1).decodeAll(new FrameCodec());
        });
    }

    @Test
    void jsonFilesWithOutOfRangeValuesAreFormatErrors() {
        assertThrows(FormatException.class, () -> SegmentGraph.fromJson(
                "{\"formatVersion\": 1, \"segments\": [{\"id\": 0, \"startTick\": 0, \"startPartial\": 1.5}]}"));
        assertThrows(FormatException.class, () -> SegmentGraph.fromJson(
                "{\"formatVersion\": 1, \"segments\": [{\"id\": 1, \"startTick\": 0}]}"));
        assertThrows(FormatException.class, () -> Marker.fromJson(
                "{\"markers\": [{\"segment\": 0, \"tick\": 1, \"partial\": -0.5, \"frame\": 0}]}"));
        assertThrows(FormatException.class, () -> RecordingMeta.fromJson("{\"formatVersion\": 1}"));
        assertThrows(FormatException.class, () -> RecordingMeta.fromJson("[]"));
    }
}
