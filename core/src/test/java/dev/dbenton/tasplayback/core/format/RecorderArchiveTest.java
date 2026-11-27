package dev.dbenton.tasplayback.core.format;

import dev.dbenton.tasplayback.core.branch.Segment;
import dev.dbenton.tasplayback.core.branch.SegmentGraph;
import dev.dbenton.tasplayback.core.event.EventCodec;
import dev.dbenton.tasplayback.core.event.EventRecord;
import dev.dbenton.tasplayback.core.frame.FrameCodec;
import dev.dbenton.tasplayback.core.frame.FrameRecord;
import dev.dbenton.tasplayback.core.sound.SoundEvent;
import dev.dbenton.tasplayback.core.time.GameTime;
import dev.dbenton.tasplayback.core.time.TimeKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecorderArchiveTest {
    @TempDir Path tmp;

    static RecordingMeta meta() {
        RecordingMeta m = new RecordingMeta();
        m.minecraftVersion = "1.21.11"; m.loader = "fabric"; m.modVersion = "test"; m.recordedAt = "2026-11-27T12:00:00Z";
        m.windowWidth = 854; m.windowHeight = 480; m.framebufferWidth = 854; m.framebufferHeight = 480; m.guiScale = 2;
        m.resourcePacks.add(new RecordingMeta.ResourcePack("vanilla", "Default", ""));
        m.resourcePacks.add(new RecordingMeta.ResourcePack("file/faithful.zip", "Faithful", "ab12"));
        m.renderOptions.put("fov", 70L);
        m.renderOptions.put("bobView", true);
        m.tickrateHistory.add(new RecordingMeta.TickrateChange(0, 0, 2.0, false, "tasmod"));
        return m;
    }

    static SegmentGraph graph() {
        SegmentGraph g = new SegmentGraph();
        g.add(Segment.root(0, new GameTime(100, 0f), 0));
        return g;
    }

    private static AsyncRecorder.Config smallChunks() {
        AsyncRecorder.Config c = new AsyncRecorder.Config();
        c.maxChunkRecords = 64;
        c.flushIntervalNanos = Long.MAX_VALUE; // chunk boundaries depend only on record counts
        return c;
    }

    @Test
    void recordPackAndReopen() throws IOException {
        Path part = tmp.resolve("run" + TaspArchive.PART_SUFFIX);
        List<FrameRecord> frames = TestFrames.generate(10, 900);
        List<EventRecord> sounds = new ArrayList<>();
        AsyncRecorder rec = AsyncRecorder.start(part, meta(), graph(), smallChunks());
        for (FrameRecord f : frames) {
            assertTrue(rec.offerFrame(f));
            if (f.key.frame % 50 == 0) {
                EventRecord e = SoundEvent.play(f.key.frame, "minecraft:ui.button.click", "minecraft:random/click", "master",
                        0.25f, 1f, 0, 0, 0, true).toRecord(f.key);
                sounds.add(e);
                assertTrue(rec.offerEvent(StreamId.SOUNDS, e));
            }
        }
        SegmentGraph g = graph();
        g.add(Segment.rewind(1, new GameTime(105, 0f), 600, 0, new GameTime(105, 0f), "savestate-load", "tasmod"));
        rec.publishSegments(g);
        rec.publishMarkers(Arrays.asList(new Marker(new TimeKey(0, 101, 0.5f, 40), "first jump", "chapter")));
        rec.close();
        assertEquals(0, rec.droppedRecords());

        TaspArchive.Recording dir = TaspArchive.open(part);
        assertFalse(dir.unfinished);
        assertTrue(dir.problems().isEmpty(), dir.problems().toString());

        Path tasp = tmp.resolve("run.tasp");
        assertTrue(TaspArchive.pack(part, tasp).isEmpty());
        TaspArchive.Recording r = TaspArchive.open(tasp);
        assertEquals("1.21.11", r.meta.minecraftVersion);
        assertEquals(2, r.meta.resourcePacks.size());
        assertEquals("ab12", r.meta.resourcePacks.get(1).sha1);
        assertEquals(true, r.meta.renderOptions.get("bobView"));
        assertEquals(2.0, r.meta.tickrateHistory.get(0).ticksPerSecond);
        assertEquals(2, r.segments.size());
        assertEquals("savestate-load", r.segments.get(1).cause);
        assertEquals("first jump", r.markers.get(0).label);
        List<FrameRecord> back = r.stream(StreamId.FRAMES).decodeAll(new FrameCodec());
        assertEquals(frames.size(), back.size());
        for (int i = 0; i < frames.size(); i++) assertTrue(frames.get(i).bitEquals(back.get(i)));
        assertEquals(sounds, r.stream(StreamId.SOUNDS).decodeAll(new EventCodec()));
        assertEquals(0, r.stream(StreamId.GUI).recordCount());

        TaspArchive.deletePartDirectory(part);
        assertFalse(Files.exists(part));
    }

    @Test
    void crashedRecordingIsRecoveredUpToLastCompleteChunk() throws IOException {
        Path part = tmp.resolve("crash" + TaspArchive.PART_SUFFIX);
        List<FrameRecord> frames = TestFrames.generate(11, 1000);
        AsyncRecorder rec = AsyncRecorder.start(part, meta(), graph(), smallChunks());
        for (FrameRecord f : frames) rec.offerFrame(f);
        rec.close();

        // Simulate a crash: status still says "recording" and the frames file was cut mid-chunk.
        Path crashed = tmp.resolve("copy" + TaspArchive.PART_SUFFIX);
        Files.createDirectories(crashed);
        for (String n : new String[] { "meta.json", "segments.json" }) Files.copy(part.resolve(n), crashed.resolve(n));
        Files.write(crashed.resolve("status.json"), "{\"state\": \"recording\"}".getBytes());
        byte[] fr = Files.readAllBytes(part.resolve("frames.bin"));
        int cut = fr.length - 37;
        Files.write(crashed.resolve("frames.bin"), Arrays.copyOf(fr, cut));
        Files.copy(part.resolve("sounds.bin"), crashed.resolve("sounds.bin"), StandardCopyOption.REPLACE_EXISTING);

        TaspArchive.Recording r = TaspArchive.open(crashed);
        assertTrue(r.unfinished);
        assertEquals(2, r.problems().size(), r.problems().toString());
        List<FrameRecord> back = r.stream(StreamId.FRAMES).decodeAll(new FrameCodec());
        assertTrue(back.size() >= 1000 - 64 && back.size() < 1000, "recovered " + back.size());
        assertEquals(0, back.size() % 64);
        for (int i = 0; i < back.size(); i++) assertTrue(frames.get(i).bitEquals(back.get(i)));

        Path tasp = tmp.resolve("recovered.tasp");
        assertFalse(TaspArchive.pack(crashed, tasp).isEmpty());
        TaspArchive.Recording packed = TaspArchive.open(tasp);
        assertTrue(packed.problems().isEmpty(), "packed archive keeps only valid chunks: " + packed.problems());
        assertEquals(back.size(), packed.stream(StreamId.FRAMES).recordCount());
    }

    @Test
    void overloadDropsInsteadOfBlockingAndCountsEveryRecord() throws IOException {
        Path part = tmp.resolve("flood" + TaspArchive.PART_SUFFIX);
        AsyncRecorder.Config c = new AsyncRecorder.Config();
        c.queueCapacity = 8;
        AsyncRecorder rec = AsyncRecorder.start(part, meta(), graph(), c);
        List<FrameRecord> frames = TestFrames.generate(12, 20000);
        int ok = 0;
        long worst = 0;
        for (FrameRecord f : frames) {
            long t0 = System.nanoTime();
            if (rec.offerFrame(f)) ok++;
            worst = Math.max(worst, System.nanoTime() - t0);
            rec.offerEvent(StreamId.GUICAP, new EventRecord(f.key, 0, new byte[4096]));
        }
        rec.close();
        long offered = 2L * frames.size();
        assertEquals(offered, rec.acceptedRecords() + rec.droppedRecords() + rec.droppedGuiCaptureRecords());
        TaspArchive.Recording r = TaspArchive.open(part);
        assertEquals(ok, r.stream(StreamId.FRAMES).recordCount());
        assertEquals(rec.acceptedRecords() - ok, r.stream(StreamId.GUICAP).recordCount());
        assertFalse(rec.offerFrame(frames.get(0)), "closed recorder accepts nothing");
        assertTrue(worst < 50_000_000L, "an offer took " + worst / 1e6 + " ms");
    }

    @Test
    void resourcePackWarnings() {
        RecordingMeta m = meta();
        List<RecordingMeta.ResourcePack> same = new ArrayList<>(m.resourcePacks);
        assertTrue(m.resourcePackWarnings(same).isEmpty());
        List<RecordingMeta.ResourcePack> changed = Arrays.asList(
                new RecordingMeta.ResourcePack("vanilla", "Default", ""),
                new RecordingMeta.ResourcePack("file/faithful.zip", "Faithful", "ffff"),
                new RecordingMeta.ResourcePack("file/extra.zip", "Extra", "1"));
        List<String> w = m.resourcePackWarnings(changed);
        assertTrue(w.stream().anyMatch(s -> s.contains("differs")), w.toString());
        assertTrue(w.stream().anyMatch(s -> s.contains("extra")), w.toString());
        List<RecordingMeta.ResourcePack> reordered = Arrays.asList(m.resourcePacks.get(1), m.resourcePacks.get(0));
        assertTrue(m.resourcePackWarnings(reordered).stream().anyMatch(s -> s.contains("order")));
        assertTrue(m.resourcePackWarnings(Arrays.asList(m.resourcePacks.get(0))).stream().anyMatch(s -> s.contains("missing")));
    }

    @Test
    void refusesToOverwriteAnExistingRecording() throws IOException {
        Path part = tmp.resolve("x" + TaspArchive.PART_SUFFIX);
        Files.createDirectories(part);
        assertThrows(IOException.class, () -> AsyncRecorder.start(part, meta(), graph(), new AsyncRecorder.Config()));
    }
}
