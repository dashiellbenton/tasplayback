package dev.dbenton.tasplayback.core.format;

import dev.dbenton.tasplayback.core.time.TimeKey;

/** One decoded (decompressed) chunk of a stream. */
public final class Chunk {
    public final int seq;
    public final int recordCount;
    public final TimeKey firstKey;
    public final TimeKey lastKey;
    /** Decompressed record bytes. */
    public final byte[] raw;
    /** Byte offset of the chunk header within the stream file. */
    public final long fileOffset;

    Chunk(int seq, int recordCount, TimeKey firstKey, TimeKey lastKey, byte[] raw, long fileOffset) {
        this.seq = seq;
        this.recordCount = recordCount;
        this.firstKey = firstKey;
        this.lastKey = lastKey;
        this.raw = raw;
        this.fileOffset = fileOffset;
    }
}
