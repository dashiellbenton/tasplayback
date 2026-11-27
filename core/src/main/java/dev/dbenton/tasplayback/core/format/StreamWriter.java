package dev.dbenton.tasplayback.core.format;

import dev.dbenton.tasplayback.core.io.ByteSink;
import dev.dbenton.tasplayback.core.time.TimeKey;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.Deflater;

/**
 * Appends records of one stream to its file as self-contained chunks. Single-threaded: owned by the
 * recorder's writer thread, never by a game thread.
 *
 * <p>A chunk is cut when it reaches {@code maxRecords} records or {@code maxRawBytes} bytes, or when
 * {@link #flush()} is called (the recorder calls it about once per second of wall time). Every written
 * chunk is pushed to the OS immediately, so a game crash loses at most the unflushed chunk.
 */
public final class StreamWriter<T> implements Closeable {
    private final StreamId stream;
    private final StreamCodec<T> codec;
    private final FileChannel channel;
    private final int maxRecords;
    private final int maxRawBytes;
    private final int level;
    private final boolean fsync;

    private final ByteSink pending = new ByteSink(64 * 1024);
    private int pendingCount;
    private TimeKey firstKey;
    private TimeKey lastKey;
    private int seq;
    private long bytesWritten;
    private long recordsWritten;

    public StreamWriter(Path file, StreamId stream, StreamCodec<T> codec, int maxRecords, int maxRawBytes, boolean fsync)
            throws IOException {
        this.stream = stream;
        this.codec = codec;
        this.maxRecords = maxRecords;
        this.maxRawBytes = maxRawBytes;
        this.level = Deflater.DEFAULT_COMPRESSION;
        this.fsync = fsync;
        this.channel = FileChannel.open(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        writeFully(ChunkFormat.fileHeader(stream, stream.version));
    }

    public static <T> StreamWriter<T> create(Path dir, StreamId stream, StreamCodec<T> codec) throws IOException {
        return new StreamWriter<>(dir.resolve(stream.fileName), stream, codec, 4096, 1 << 20, false);
    }

    public StreamId stream() { return stream; }
    public long bytesWritten() { return bytesWritten; }
    public long recordsWritten() { return recordsWritten; }

    public void write(T record) throws IOException {
        if (pendingCount == 0) {
            codec.beginChunk();
            firstKey = codec.keyOf(record);
        }
        codec.encode(record, pending);
        lastKey = codec.keyOf(record);
        pendingCount++;
        recordsWritten++;
        if (pendingCount >= maxRecords || pending.size() >= maxRawBytes) flush();
    }

    /** Cuts the pending records into a chunk and writes it. No-op when nothing is pending. */
    public void flush() throws IOException {
        if (pendingCount == 0) return;
        byte[] chunk = ChunkFormat.encodeChunk(seq++, pendingCount, pending.array(), pending.size(), firstKey, lastKey, level);
        writeFully(chunk);
        if (fsync) channel.force(false);
        pending.reset();
        pendingCount = 0;
    }

    private void writeFully(byte[] b) throws IOException {
        ByteBuffer buf = ByteBuffer.wrap(b);
        while (buf.hasRemaining()) channel.write(buf);
        bytesWritten += b.length;
    }

    @Override public void close() throws IOException {
        try {
            flush();
            channel.force(true);
        } finally {
            channel.close();
        }
    }
}
