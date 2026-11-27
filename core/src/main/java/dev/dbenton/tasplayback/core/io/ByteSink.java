package dev.dbenton.tasplayback.core.io;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Growable big-endian byte buffer with varint helpers. Not thread-safe. */
public final class ByteSink {
    private byte[] buf;
    private int size;

    public ByteSink() { this(256); }
    public ByteSink(int initialCapacity) { buf = new byte[Math.max(16, initialCapacity)]; }

    private void ensure(int extra) {
        int need = size + extra;
        if (need > buf.length) buf = Arrays.copyOf(buf, Math.max(need, buf.length * 2));
    }

    public int size() { return size; }
    public void reset() { size = 0; }
    public byte[] array() { return buf; }
    public byte[] toByteArray() { return Arrays.copyOf(buf, size); }

    public ByteSink writeByte(int b) { ensure(1); buf[size++] = (byte) b; return this; }
    public ByteSink writeBoolean(boolean b) { return writeByte(b ? 1 : 0); }

    public ByteSink writeBytes(byte[] b) { return writeBytes(b, 0, b.length); }
    public ByteSink writeBytes(byte[] b, int off, int len) {
        ensure(len); System.arraycopy(b, off, buf, size, len); size += len; return this;
    }

    public ByteSink writeShort(int v) { ensure(2); buf[size++] = (byte) (v >>> 8); buf[size++] = (byte) v; return this; }
    public ByteSink writeInt(int v) {
        ensure(4);
        buf[size++] = (byte) (v >>> 24); buf[size++] = (byte) (v >>> 16);
        buf[size++] = (byte) (v >>> 8); buf[size++] = (byte) v;
        return this;
    }
    public ByteSink writeLong(long v) { writeInt((int) (v >>> 32)); return writeInt((int) v); }
    public ByteSink writeFloat(float v) { return writeInt(Float.floatToRawIntBits(v)); }
    public ByteSink writeDouble(double v) { return writeLong(Double.doubleToRawLongBits(v)); }

    /** Unsigned LEB128. */
    public ByteSink writeVarLong(long v) {
        ensure(10);
        while ((v & ~0x7FL) != 0) { buf[size++] = (byte) ((v & 0x7F) | 0x80); v >>>= 7; }
        buf[size++] = (byte) v;
        return this;
    }
    public ByteSink writeVarInt(int v) { return writeVarLong(v & 0xFFFFFFFFL); }
    public ByteSink writeZigZagLong(long v) { return writeVarLong(VarInts.zigZag(v)); }
    public ByteSink writeZigZagInt(int v) { return writeVarLong(VarInts.zigZag(v) & 0xFFFFFFFFL); }

    public ByteSink writeString(String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        writeVarInt(b.length);
        return writeBytes(b);
    }

    /** Nullable string: 0 = null, otherwise length+1. */
    public ByteSink writeNullableString(String s) {
        if (s == null) return writeVarInt(0);
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        writeVarInt(b.length + 1);
        return writeBytes(b);
    }

    public ByteSink writeByteArray(byte[] b) { writeVarInt(b.length); return writeBytes(b); }

    /** Overwrites 4 bytes at {@code pos} (big-endian). */
    public void setInt(int pos, int v) {
        buf[pos] = (byte) (v >>> 24); buf[pos + 1] = (byte) (v >>> 16);
        buf[pos + 2] = (byte) (v >>> 8); buf[pos + 3] = (byte) v;
    }
}
