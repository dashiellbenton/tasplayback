package dev.dbenton.tasplayback.core.format;

import dev.dbenton.tasplayback.core.io.ByteSource;
import dev.dbenton.tasplayback.core.io.FormatException;
import dev.dbenton.tasplayback.core.time.TimeKey;
import dev.dbenton.tasplayback.core.time.TimeKeyCodec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reads a stream file into chunks, tolerating damage at the tail.
 *
 * <p>Reading stops at the first chunk that is truncated or fails a checksum; everything before it is
 * kept, so a recording cut off by a crash recovers up to its last complete chunk. {@link Result#problem}
 * says why reading stopped early ({@code null} when the file was clean).
 */
public final class StreamReader {
    private StreamReader() {}

    public static final class Result {
        public final StreamId stream;
        public final int version;
        public final List<Chunk> chunks;
        /** Bytes that were part of valid chunks (including the file header). */
        public final long validBytes;
        public final long totalBytes;
        /** Why reading stopped before the end of the data, or null if the whole file was valid. */
        public final String problem;

        Result(StreamId stream, int version, List<Chunk> chunks, long validBytes, long totalBytes, String problem) {
            this.stream = stream;
            this.version = version;
            this.chunks = Collections.unmodifiableList(chunks);
            this.validBytes = validBytes;
            this.totalBytes = totalBytes;
            this.problem = problem;
        }

        public boolean clean() { return problem == null; }

        public int recordCount() {
            int n = 0;
            for (Chunk c : chunks) n += c.recordCount;
            return n;
        }

        /** Decodes every record using a fresh codec instance. */
        public <T> List<T> decodeAll(StreamCodec<T> codec) {
            List<T> out = new ArrayList<>(recordCount());
            for (Chunk c : chunks) out.addAll(decodeChunk(c, codec));
            return out;
        }
    }

    public static <T> List<T> decodeChunk(Chunk c, StreamCodec<T> codec) {
        codec.beginChunk();
        ByteSource in = new ByteSource(c.raw);
        List<T> out = new ArrayList<>(c.recordCount);
        for (int i = 0; i < c.recordCount; i++) out.add(codec.decode(in));
        if (in.hasRemaining()) throw new FormatException("chunk " + c.seq + " has " + in.remaining() + " trailing bytes");
        return out;
    }

    /**
     * Parses a whole stream file. Throws {@link FormatException} only if the file header itself is bad
     * or the stream version is newer than {@code maxVersion}; chunk-level damage is reported via {@link Result#problem}.
     */
    public static Result read(byte[] data, StreamId expected, int maxVersion) {
        ChunkFormat.FileHeader h = ChunkFormat.readFileHeader(data);
        if (expected != null && h.stream != expected) {
            throw new FormatException("expected stream " + expected + " but file holds " + h.stream);
        }
        if (h.version > maxVersion) {
            throw new FormatException(h.stream + " stream version " + h.version + " is newer than supported " + maxVersion);
        }
        List<Chunk> chunks = new ArrayList<>();
        int pos = ChunkFormat.FILE_HEADER_SIZE;
        String problem = null;
        int expectSeq = 0;
        while (pos < data.length) {
            if (data.length - pos < ChunkFormat.CHUNK_HEADER_SIZE) { problem = "truncated chunk header at byte " + pos; break; }
            ByteSource in = new ByteSource(data, pos, ChunkFormat.CHUNK_HEADER_SIZE);
            int magic = in.readInt();
            if (magic != ChunkFormat.CHUNK_MAGIC) { problem = "bad chunk magic at byte " + pos; break; }
            int seq = in.readInt();
            int count = in.readInt();
            int rawLen = in.readInt();
            int bodyLen = in.readInt();
            TimeKey first, last;
            try {
                first = TimeKeyCodec.readFixed(in);
                last = TimeKeyCodec.readFixed(in);
            } catch (FormatException e) { problem = "corrupt chunk header at byte " + pos; break; }
            int bodyCrc = in.readInt();
            int headerCrc = in.readInt();
            if (headerCrc != ChunkFormat.crc(data, pos, ChunkFormat.CHUNK_HEADER_SIZE - 4)) {
                problem = "chunk header checksum mismatch at byte " + pos; break;
            }
            if (seq != expectSeq) { problem = "chunk sequence gap at byte " + pos + " (got " + seq + ", expected " + expectSeq + ")"; break; }
            if (count <= 0 || rawLen < 0 || bodyLen < 0 || rawLen > ChunkFormat.MAX_CHUNK_BYTES || bodyLen > ChunkFormat.MAX_CHUNK_BYTES) {
                problem = "implausible chunk sizes at byte " + pos; break;
            }
            int bodyStart = pos + ChunkFormat.CHUNK_HEADER_SIZE;
            if (data.length - bodyStart < bodyLen) { problem = "truncated chunk body at byte " + pos; break; }
            if (bodyCrc != ChunkFormat.crc(data, bodyStart, bodyLen)) { problem = "chunk body checksum mismatch at byte " + pos; break; }
            byte[] raw;
            try {
                raw = ChunkFormat.inflate(data, bodyStart, bodyLen, rawLen);
            } catch (FormatException e) { problem = "chunk " + seq + ": " + e.getMessage(); break; }
            chunks.add(new Chunk(seq, count, first, last, raw, pos));
            expectSeq++;
            pos = bodyStart + bodyLen;
        }
        long valid = problem == null ? data.length : pos;
        return new Result(h.stream, h.version, chunks, valid, data.length, problem);
    }
}
