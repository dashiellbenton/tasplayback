package dev.dbenton.tasplayback.core.format;

import dev.dbenton.tasplayback.core.branch.SegmentGraph;
import dev.dbenton.tasplayback.core.event.EventCodec;
import dev.dbenton.tasplayback.core.event.EventRecord;
import dev.dbenton.tasplayback.core.frame.FrameCodec;
import dev.dbenton.tasplayback.core.frame.FrameRecord;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Writes a recording into a {@code .tasp.part} directory on a dedicated thread (PLAN.md §4.2).
 *
 * <p><b>Game threads never block here and never do I/O.</b> {@link #offerFrame} and {@link #offerEvent}
 * only enqueue the record into a bounded queue and return immediately. When the queue is more than half
 * full, GUICAP records (the heavy optional stream) are dropped first; when it is completely full, the
 * record is dropped and counted, and {@link #overloaded()} turns true so the UI can warn the user.
 * Records handed to the recorder must not be modified afterwards.
 *
 * <p>Segments and markers are published as snapshots ({@link #publishSegments}), which never go through
 * the bounded queue and therefore can't be dropped; the writer thread persists the latest snapshot.
 */
public final class AsyncRecorder implements Closeable {
    public static final String STATUS_FILE = "status.json";
    public static final String META_FILE = "meta.json";
    public static final String SEGMENTS_FILE = "segments.json";
    public static final String MARKERS_FILE = "markers.json";

    public static final class Config {
        public int queueCapacity = 1 << 16;
        public long flushIntervalNanos = TimeUnit.SECONDS.toNanos(1);
        public int maxChunkRecords = 4096;
        public int maxChunkBytes = 1 << 20;
        public boolean fsync = false;
    }

    private static final class Item {
        final StreamId stream;
        final Object record;
        Item(StreamId stream, Object record) { this.stream = stream; this.record = record; }
    }

    private static final Item STOP = new Item(null, null);

    private final Path dir;
    private final Config config;
    private final BlockingQueue<Item> queue;
    private final Map<StreamId, StreamWriter<?>> writers = new EnumMap<>(StreamId.class);
    private final Thread thread;
    private final AtomicReference<String> pendingSegments = new AtomicReference<>();
    private final AtomicReference<String> pendingMarkers = new AtomicReference<>();
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong droppedGuiCapture = new AtomicLong();
    private final AtomicLong accepted = new AtomicLong();
    private volatile boolean overloaded;
    private volatile boolean closed;

    private AsyncRecorder(Path dir, Config config) {
        this.dir = dir;
        this.config = config;
        this.queue = new ArrayBlockingQueue<>(config.queueCapacity);
        this.thread = new Thread(this::run, "TASPlayback-RecordingWriter");
        this.thread.setDaemon(true);
        this.thread.setPriority(Thread.NORM_PRIORITY - 1);
    }

    /** Creates the part directory, writes meta.json and starts the writer thread. Call from a non-game thread or at recording start. */
    public static AsyncRecorder start(Path partDir, RecordingMeta meta, SegmentGraph segments, Config config) throws IOException {
        if (Files.exists(partDir)) throw new IOException("recording directory already exists: " + partDir);
        Files.createDirectories(partDir);
        AsyncRecorder r = new AsyncRecorder(partDir, config);
        writeAtomically(partDir.resolve(META_FILE), meta.toJsonString());
        writeAtomically(partDir.resolve(SEGMENTS_FILE), segments.toJsonString());
        writeAtomically(partDir.resolve(STATUS_FILE), "{\n  \"state\": \"recording\"\n}\n");
        r.writers.put(StreamId.FRAMES, new StreamWriter<>(partDir.resolve(StreamId.FRAMES.fileName), StreamId.FRAMES,
                new FrameCodec(), config.maxChunkRecords, config.maxChunkBytes, config.fsync));
        for (StreamId s : StreamId.values()) {
            if (s == StreamId.FRAMES) continue;
            r.writers.put(s, new StreamWriter<>(partDir.resolve(s.fileName), s, new EventCodec(),
                    config.maxChunkRecords, config.maxChunkBytes, config.fsync));
        }
        r.thread.start();
        return r;
    }

    // ------------------------------------------------------------------ producer side (game threads)

    public boolean offerFrame(FrameRecord r) { return offer(StreamId.FRAMES, r); }

    public boolean offerEvent(StreamId stream, EventRecord e) {
        if (stream == StreamId.FRAMES) throw new IllegalArgumentException("frames go through offerFrame");
        return offer(stream, e);
    }

    private boolean offer(StreamId stream, Object record) {
        if (closed || failure.get() != null) { dropped.incrementAndGet(); return false; }
        if (stream == StreamId.GUICAP && queue.size() * 2 > config.queueCapacity) {
            droppedGuiCapture.incrementAndGet();
            return false;
        }
        if (!queue.offer(new Item(stream, record))) {
            dropped.incrementAndGet();
            overloaded = true;
            return false;
        }
        accepted.incrementAndGet();
        return true;
    }

    /** Publishes the current segment graph; the writer thread persists it soon. Never blocks. */
    public void publishSegments(SegmentGraph g) { pendingSegments.set(g.toJsonString()); }

    public void publishMarkers(List<Marker> markers) { pendingMarkers.set(Marker.toJson(markers)); }

    public boolean overloaded() { return overloaded; }
    public long droppedRecords() { return dropped.get(); }
    public long droppedGuiCaptureRecords() { return droppedGuiCapture.get(); }
    public long acceptedRecords() { return accepted.get(); }
    public Throwable failure() { return failure.get(); }
    public Path directory() { return dir; }

    // ------------------------------------------------------------------ writer thread

    @SuppressWarnings("unchecked")
    private void run() {
        long lastFlush = System.nanoTime();
        try {
            while (true) {
                Item it = queue.poll(50, TimeUnit.MILLISECONDS);
                if (it == STOP) break;
                if (it != null) ((StreamWriter<Object>) writers.get(it.stream)).write(it.record);
                persistSnapshots();
                long now = System.nanoTime();
                if (now - lastFlush >= config.flushIntervalNanos) {
                    for (StreamWriter<?> w : writers.values()) w.flush();
                    lastFlush = now;
                }
            }
            // drain whatever is left after STOP (nothing can be enqueued once closed is set)
            Item it;
            while ((it = queue.poll()) != null) {
                if (it != STOP) ((StreamWriter<Object>) writers.get(it.stream)).write(it.record);
            }
            persistSnapshots();
        } catch (Throwable t) {
            failure.compareAndSet(null, t);
        } finally {
            for (StreamWriter<?> w : writers.values()) {
                try { w.close(); } catch (IOException e) { failure.compareAndSet(null, e); }
            }
        }
    }

    private void persistSnapshots() throws IOException {
        String s = pendingSegments.getAndSet(null);
        if (s != null) writeAtomically(dir.resolve(SEGMENTS_FILE), s);
        String m = pendingMarkers.getAndSet(null);
        if (m != null) writeAtomically(dir.resolve(MARKERS_FILE), m);
    }

    static void writeAtomically(Path target, String content) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(tmp, content.getBytes(StandardCharsets.UTF_8));
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Stops accepting records, writes everything still queued, closes the stream files and marks the
     * directory complete. Blocks until the writer thread is done; call it off the render thread.
     *
     * @throws IOException if the writer thread failed at any point
     */
    @Override public void close() throws IOException {
        if (closed) return;
        closed = true;
        while (true) {
            try {
                queue.put(STOP);
                break;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        try {
            thread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while finishing the recording", e);
        }
        Throwable f = failure.get();
        if (f != null) throw new IOException("recording writer failed", f);
        writeAtomically(dir.resolve(STATUS_FILE), "{\n  \"state\": \"complete\",\n  \"droppedRecords\": " + dropped.get()
                + ",\n  \"droppedGuiCaptureRecords\": " + droppedGuiCapture.get() + "\n}\n");
    }
}
