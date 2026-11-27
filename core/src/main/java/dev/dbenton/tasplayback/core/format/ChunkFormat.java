package dev.dbenton.tasplayback.core.format;

import dev.dbenton.tasplayback.core.io.ByteSink;
import dev.dbenton.tasplayback.core.io.ByteSource;
import dev.dbenton.tasplayback.core.io.FormatException;
import dev.dbenton.tasplayback.core.time.TimeKey;
import dev.dbenton.tasplayback.core.time.TimeKeyCodec;

import java.io.ByteArrayOutputStream;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Byte layout of a stream file.
 *
 * <pre>
 * file   := fileHeader chunk*
 * fileHeader (12 bytes) := magic "TASP" | u8 kind (=1 stream) | u8 streamId | u16 streamVersion | u32 crc32(previous 8 bytes)
 * chunk  := chunkHeader body
 * chunkHeader (76 bytes) := magic "CHNK" | u32 seq | u32 recordCount | u32 rawLength | u32 bodyLength
 *                          | TimeKey firstKey (24) | TimeKey lastKey (24) | u32 crc32(body) | u32 crc32(previous 72 header bytes)
 * body   := deflate(records), records encoded by the stream's {@link StreamCodec} after beginChunk()
 * </pre>
 * All integers big-endian.
 */
public final class ChunkFormat {
    private ChunkFormat() {}

    public static final int FILE_MAGIC = 0x54415350;   // "TASP"
    public static final int CHUNK_MAGIC = 0x43484E4B;  // "CHNK"
    public static final int KIND_STREAM = 1;
    public static final int FILE_HEADER_SIZE = 12;
    public static final int CHUNK_HEADER_SIZE = 4 * 5 + TimeKeyCodec.FIXED_SIZE * 2 + 4 + 4;
    /** Refuse absurd sizes from corrupt headers (64 MiB). */
    public static final int MAX_CHUNK_BYTES = 64 << 20;

    public static byte[] fileHeader(StreamId stream, int version) {
        ByteSink s = new ByteSink(FILE_HEADER_SIZE);
        s.writeInt(FILE_MAGIC).writeByte(KIND_STREAM).writeByte(stream.id).writeShort(version);
        s.writeInt(crc(s.array(), 0, s.size()));
        return s.toByteArray();
    }

    /** Parsed file header. */
    public static final class FileHeader {
        public final StreamId stream;
        public final int version;
        FileHeader(StreamId stream, int version) { this.stream = stream; this.version = version; }
    }

    public static FileHeader readFileHeader(byte[] data) {
        if (data.length < FILE_HEADER_SIZE) throw new FormatException("stream file shorter than its header");
        ByteSource in = new ByteSource(data, 0, FILE_HEADER_SIZE);
        if (in.readInt() != FILE_MAGIC) throw new FormatException("not a TASPlayback stream file");
        if (in.readUnsignedByte() != KIND_STREAM) throw new FormatException("unexpected file kind");
        int sid = in.readUnsignedByte();
        int version = in.readUnsignedShort();
        int crc = in.readInt();
        if (crc != crc(data, 0, 8)) throw new FormatException("stream file header checksum mismatch");
        StreamId stream;
        try { stream = StreamId.byId(sid); } catch (IllegalArgumentException e) { throw new FormatException(e.getMessage()); }
        return new FileHeader(stream, version);
    }

    /** Encodes one chunk (header + compressed body). */
    public static byte[] encodeChunk(int seq, int recordCount, byte[] raw, int rawLen, TimeKey first, TimeKey last, int level) {
        byte[] body = deflate(raw, rawLen, level);
        ByteSink s = new ByteSink(CHUNK_HEADER_SIZE + body.length);
        s.writeInt(CHUNK_MAGIC).writeInt(seq).writeInt(recordCount).writeInt(rawLen).writeInt(body.length);
        TimeKeyCodec.writeFixed(s, first);
        TimeKeyCodec.writeFixed(s, last);
        s.writeInt(crc(body, 0, body.length));
        s.writeInt(crc(s.array(), 0, s.size()));
        s.writeBytes(body);
        return s.toByteArray();
    }

    public static int crc(byte[] b, int off, int len) {
        CRC32 c = new CRC32();
        c.update(b, off, len);
        return (int) c.getValue();
    }

    static byte[] deflate(byte[] raw, int len, int level) {
        Deflater d = new Deflater(level, true);
        try {
            d.setInput(raw, 0, len);
            d.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, len / 2));
            byte[] buf = new byte[8192];
            while (!d.finished()) {
                int n = d.deflate(buf);
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } finally {
            d.end();
        }
    }

    static byte[] inflate(byte[] body, int off, int len, int rawLen) {
        if (rawLen == 0) return new byte[0];
        Inflater inf = new Inflater(true);
        try {
            // With nowrap=true the inflater needs one extra dummy input byte (see Inflater javadoc).
            byte[] in = new byte[len + 1];
            System.arraycopy(body, off, in, 0, len);
            inf.setInput(in);
            byte[] out = new byte[rawLen];
            int n = 0;
            while (n < rawLen && !inf.finished()) {
                int r = inf.inflate(out, n, rawLen - n);
                if (r == 0 && (inf.needsInput() || inf.needsDictionary())) break;
                n += r;
            }
            if (n != rawLen || !inf.finished()) {
                throw new FormatException("chunk body inflated to " + n + " bytes, expected " + rawLen);
            }
            return out;
        } catch (DataFormatException e) {
            throw new FormatException("corrupt chunk body", e);
        } finally {
            inf.end();
        }
    }
}
