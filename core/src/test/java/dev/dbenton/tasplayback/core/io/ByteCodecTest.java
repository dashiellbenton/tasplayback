package dev.dbenton.tasplayback.core.io;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class ByteCodecTest {
    @Test
    void varintsRoundTripEdgeAndRandomValues() {
        long[] edge = { 0, 1, -1, 63, 64, 127, 128, 255, 16383, 16384, Integer.MAX_VALUE, Integer.MIN_VALUE, Long.MAX_VALUE, Long.MIN_VALUE };
        Random rnd = new Random(42);
        ByteSink s = new ByteSink();
        long[] vals = new long[edge.length + 1000];
        System.arraycopy(edge, 0, vals, 0, edge.length);
        for (int i = edge.length; i < vals.length; i++) vals[i] = rnd.nextLong() >> rnd.nextInt(64);
        for (long v : vals) { s.writeZigZagLong(v); s.writeVarLong(v); s.writeZigZagInt((int) v); }
        ByteSource in = new ByteSource(s.toByteArray());
        for (long v : vals) {
            assertEquals(v, in.readZigZagLong());
            assertEquals(v, in.readVarLong());
            assertEquals((int) v, in.readZigZagInt());
        }
        assertFalse(in.hasRemaining());
    }

    @Test
    void primitivesAndStringsRoundTrip() {
        ByteSink s = new ByteSink(1);
        s.writeByte(0xAB).writeBoolean(true).writeShort(0xBEEF).writeInt(-5).writeLong(Long.MIN_VALUE)
                .writeFloat(Float.NaN).writeDouble(-0.0).writeString("héllo ✓").writeNullableString(null)
                .writeNullableString("").writeByteArray(new byte[] { 1, 2, 3 });
        ByteSource in = new ByteSource(s.toByteArray());
        assertEquals(0xAB, in.readUnsignedByte());
        assertTrue(in.readBoolean());
        assertEquals(0xBEEF, in.readUnsignedShort());
        assertEquals(-5, in.readInt());
        assertEquals(Long.MIN_VALUE, in.readLong());
        assertEquals(Float.floatToRawIntBits(Float.NaN), Float.floatToRawIntBits(in.readFloat()));
        assertEquals(Double.doubleToRawLongBits(-0.0), Double.doubleToRawLongBits(in.readDouble()));
        assertEquals("héllo ✓", in.readString());
        assertNull(in.readNullableString());
        assertEquals("", in.readNullableString());
        assertArrayEquals(new byte[] { 1, 2, 3 }, in.readByteArray());
    }

    @Test
    void underflowAndBadLengthsThrowFormatException() {
        assertThrows(FormatException.class, () -> new ByteSource(new byte[] { 1, 2 }).readInt());
        assertThrows(FormatException.class, () -> new ByteSource(new byte[] { (byte) 0x80 }).readVarLong());
        ByteSink s = new ByteSink().writeVarInt(1000);
        assertThrows(FormatException.class, () -> new ByteSource(s.toByteArray()).readString());
        byte[] tooLong = new byte[11];
        java.util.Arrays.fill(tooLong, (byte) 0xFF);
        assertThrows(FormatException.class, () -> new ByteSource(tooLong).readVarLong());
        assertThrows(FormatException.class, () -> new ByteSource(new byte[] { 2 }).readBoolean());
    }
}
