package dev.dbenton.tasplayback.core.io;

import java.nio.charset.StandardCharsets;

/** Bounds-checked big-endian reader over a byte array. Throws {@link FormatException} on underflow. */
public final class ByteSource {
    private final byte[] buf;
    private int pos;
    private final int end;

    public ByteSource(byte[] buf) { this(buf, 0, buf.length); }
    public ByteSource(byte[] buf, int off, int len) {
        if (off < 0 || len < 0 || off + len > buf.length) throw new IllegalArgumentException("bad range");
        this.buf = buf; this.pos = off; this.end = off + len;
    }

    public int position() { return pos; }
    public int remaining() { return end - pos; }
    public boolean hasRemaining() { return pos < end; }

    private void need(int n) {
        if (n < 0 || end - pos < n) throw new FormatException("unexpected end of data (need " + n + ", have " + (end - pos) + ")");
    }

    public int readUnsignedByte() { need(1); return buf[pos++] & 0xFF; }
    public byte readByte() { need(1); return buf[pos++]; }
    public boolean readBoolean() {
        int b = readUnsignedByte();
        if (b > 1) throw new FormatException("invalid boolean " + b);
        return b == 1;
    }
    public int readUnsignedShort() { need(2); int v = ((buf[pos] & 0xFF) << 8) | (buf[pos + 1] & 0xFF); pos += 2; return v; }
    public int readInt() {
        need(4);
        int v = ((buf[pos] & 0xFF) << 24) | ((buf[pos + 1] & 0xFF) << 16) | ((buf[pos + 2] & 0xFF) << 8) | (buf[pos + 3] & 0xFF);
        pos += 4; return v;
    }
    public long readLong() { long hi = readInt() & 0xFFFFFFFFL; long lo = readInt() & 0xFFFFFFFFL; return (hi << 32) | lo; }
    public float readFloat() { return Float.intBitsToFloat(readInt()); }
    public double readDouble() { return Double.longBitsToDouble(readLong()); }

    public long readVarLong() {
        long v = 0; int shift = 0;
        while (true) {
            int b = readUnsignedByte();
            if (shift == 63 && (b & 0x7E) != 0) throw new FormatException("varint overflow");
            v |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return v;
            shift += 7;
            if (shift > 63) throw new FormatException("varint too long");
        }
    }
    public int readVarInt() {
        long v = readVarLong();
        if ((v >>> 32) != 0) throw new FormatException("varint exceeds 32 bits");
        return (int) v;
    }
    public long readZigZagLong() { return VarInts.unZigZag(readVarLong()); }
    public int readZigZagInt() { return (int) VarInts.unZigZag(readVarInt() & 0xFFFFFFFFL); }

    /** Reads a varint length and checks it against the remaining bytes. */
    public int readLength() {
        int n = readVarInt();
        if (n < 0 || n > remaining()) throw new FormatException("length " + n + " exceeds remaining " + remaining());
        return n;
    }

    public byte[] readBytes(int n) { need(n); byte[] out = new byte[n]; System.arraycopy(buf, pos, out, 0, n); pos += n; return out; }
    public byte[] readByteArray() { return readBytes(readLength()); }

    public String readString() { int n = readLength(); String s = new String(buf, pos, n, StandardCharsets.UTF_8); pos += n; return s; }
    public String readNullableString() {
        int n = readVarInt();
        if (n == 0) return null;
        n -= 1;
        if (n > remaining()) throw new FormatException("string length exceeds data");
        String s = new String(buf, pos, n, StandardCharsets.UTF_8); pos += n; return s;
    }
}
