package dev.dbenton.tasplayback.core.format;

import dev.dbenton.tasplayback.core.event.EventCodec;
import dev.dbenton.tasplayback.core.event.EventRecord;
import dev.dbenton.tasplayback.core.frame.FrameCodec;
import dev.dbenton.tasplayback.core.frame.FrameRecord;
import dev.dbenton.tasplayback.core.io.FormatException;
import dev.dbenton.tasplayback.core.time.TimeKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class StreamRoundTripTest {
    @TempDir Path tmp;

    private byte[] writeFrames(List<FrameRecord> frames, int maxRecords) throws IOException {
        Path f = tmp.resolve("frames-" + maxRecords + ".bin");
        try (StreamWriter<FrameRecord> w = new StreamWriter<>(f, StreamId.FRAMES, new FrameCodec(), maxRecords, 1 << 20, false)) {
            for (FrameRecord r : frames) w.write(r);
        }
        return Files.readAllBytes(f);
    }

    @Test
    void framesRoundTripBitExactAcrossManyChunks() throws IOException {
        List<FrameRecord> frames = TestFrames.generate(1, 5000);
        byte[] data = writeFrames(frames, 333);
        StreamReader.Result r = StreamReader.read(data, StreamId.FRAMES, StreamId.FRAMES.version);
        assertTrue(r.clean(), r.problem);
        assertEquals((5000 + 332) / 333, r.chunks.size());
        List<FrameRecord> back = r.decodeAll(new FrameCodec());
        assertEquals(frames.size(), back.size());
        for (int i = 0; i < frames.size(); i++) assertTrue(frames.get(i).bitEquals(back.get(i)), "frame " + i);
        // each chunk records its first/last keys for seeking
        assertEquals(frames.get(0).key, r.chunks.get(0).firstKey);
        assertEquals(frames.get(332).key, r.chunks.get(0).lastKey);
    }

    @Test
    void deltaCodingKeepsFramesSmall() throws IOException {
        List<FrameRecord> frames = TestFrames.generate(2, 3000);
        byte[] data = writeFrames(frames, 4096);
        // a frame is ~60 fields; the compressed stream should average well under 100 bytes per frame
        assertTrue(data.length / 3000.0 < 100, "bytes per frame: " + data.length / 3000.0);
    }

    @Test
    void eventsRoundTrip() throws IOException {
        Random rnd = new Random(3);
        List<EventRecord> events = new ArrayList<>();
        for (int i = 0; i < 2000; i++) {
            byte[] p = new byte[rnd.nextInt(40)];
            rnd.nextBytes(p);
            events.add(new EventRecord(new TimeKey(i / 700, 10 + i / 3, (i % 3) / 3f, i), rnd.nextInt(20), p));
        }
        Path f = tmp.resolve("gui.bin");
        try (StreamWriter<EventRecord> w = new StreamWriter<>(f, StreamId.GUI, new EventCodec(), 128, 1 << 20, false)) {
            for (EventRecord e : events) w.write(e);
        }
        StreamReader.Result r = StreamReader.read(Files.readAllBytes(f), StreamId.GUI, 1);
        assertTrue(r.clean());
        assertEquals(events, r.decodeAll(new EventCodec()));
    }

    @Test
    void truncationAtAnyPointRecoversAllCompleteChunks() throws IOException {
        List<FrameRecord> frames = TestFrames.generate(4, 1200);
        byte[] data = writeFrames(frames, 100);
        StreamReader.Result full = StreamReader.read(data, StreamId.FRAMES, 1);
        Random rnd = new Random(5);
        for (int trial = 0; trial < 300; trial++) {
            int cut = ChunkFormat.FILE_HEADER_SIZE + rnd.nextInt(data.length - ChunkFormat.FILE_HEADER_SIZE);
            byte[] part = Arrays.copyOf(data, cut);
            StreamReader.Result r = StreamReader.read(part, StreamId.FRAMES, 1);
            // the number of chunks recovered = chunks that end at or before the cut
            int expected = 0;
            for (int c = 0; c < full.chunks.size(); c++) {
                long end = c + 1 < full.chunks.size() ? full.chunks.get(c + 1).fileOffset : data.length;
                if (end <= cut) expected++;
            }
            assertEquals(expected, r.chunks.size(), "cut at " + cut);
            // a cut exactly on a chunk boundary is indistinguishable from a complete file (status.json covers that case)
            assertEquals(r.validBytes == cut, r.clean());
            List<FrameRecord> back = r.decodeAll(new FrameCodec());
            for (int i = 0; i < back.size(); i++) assertTrue(frames.get(i).bitEquals(back.get(i)));
        }
    }

    @Test
    void corruptionStopsReadingAtTheDamagedChunk() throws IOException {
        List<FrameRecord> frames = TestFrames.generate(6, 1000);
        byte[] data = writeFrames(frames, 100);
        StreamReader.Result full = StreamReader.read(data, StreamId.FRAMES, 1);
        Random rnd = new Random(7);
        for (int trial = 0; trial < 200; trial++) {
            byte[] bad = data.clone();
            int pos = ChunkFormat.FILE_HEADER_SIZE + rnd.nextInt(data.length - ChunkFormat.FILE_HEADER_SIZE);
            bad[pos] ^= (byte) (1 + rnd.nextInt(255));
            int damaged = 0;
            while (damaged + 1 < full.chunks.size() && full.chunks.get(damaged + 1).fileOffset <= pos) damaged++;
            StreamReader.Result r = StreamReader.read(bad, StreamId.FRAMES, 1);
            assertFalse(r.clean(), "flip at " + pos);
            assertEquals(damaged, r.chunks.size(), "flip at " + pos);
            assertEquals(full.chunks.get(damaged).fileOffset, r.validBytes);
        }
    }

    @Test
    void rejectsWrongStreamNewerVersionAndBadHeader() {
        byte[] gui = ChunkFormat.fileHeader(StreamId.GUI, 1);
        assertThrows(FormatException.class, () -> StreamReader.read(gui, StreamId.FRAMES, 1));
        byte[] future = ChunkFormat.fileHeader(StreamId.FRAMES, 9);
        assertThrows(FormatException.class, () -> StreamReader.read(future, StreamId.FRAMES, 1));
        byte[] broken = gui.clone();
        broken[5] ^= 1;
        assertThrows(FormatException.class, () -> StreamReader.read(broken, null, 1));
        assertThrows(FormatException.class, () -> StreamReader.read(new byte[3], null, 1));
    }
}
