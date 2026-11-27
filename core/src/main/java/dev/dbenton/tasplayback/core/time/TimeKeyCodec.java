package dev.dbenton.tasplayback.core.time;

import dev.dbenton.tasplayback.core.io.ByteSink;
import dev.dbenton.tasplayback.core.io.ByteSource;

/** Fixed-width and delta encodings for {@link TimeKey}. */
public final class TimeKeyCodec {
    private TimeKeyCodec() {}

    public static final TimeKey ORIGIN = new TimeKey(0, 0, 0f, 0);
    public static final int FIXED_SIZE = 4 + 8 + 4 + 8;

    public static void writeFixed(ByteSink out, TimeKey k) {
        out.writeInt(k.segment).writeLong(k.tick).writeFloat(k.partial).writeLong(k.frame);
    }

    public static TimeKey readFixed(ByteSource in) {
        int seg = in.readInt();
        long tick = in.readLong();
        float partial = in.readFloat();
        long frame = in.readLong();
        return checked(seg, tick, partial, frame);
    }

    /** Lossless delta against {@code prev}: zigzag varints for integers, XOR of raw bits for the partial. */
    public static void writeDelta(ByteSink out, TimeKey k, TimeKey prev) {
        out.writeZigZagInt(k.segment - prev.segment);
        out.writeZigZagLong(k.tick - prev.tick);
        out.writeVarInt(Float.floatToRawIntBits(k.partial) ^ Float.floatToRawIntBits(prev.partial));
        out.writeZigZagLong(k.frame - prev.frame);
    }

    public static TimeKey readDelta(ByteSource in, TimeKey prev) {
        int seg = prev.segment + in.readZigZagInt();
        long tick = prev.tick + in.readZigZagLong();
        float partial = Float.intBitsToFloat(in.readVarInt() ^ Float.floatToRawIntBits(prev.partial));
        long frame = prev.frame + in.readZigZagLong();
        return checked(seg, tick, partial, frame);
    }

    private static TimeKey checked(int seg, long tick, float partial, long frame) {
        if (!(partial >= 0f && partial < 1f)) {
            throw new dev.dbenton.tasplayback.core.io.FormatException("corrupt time key: partial " + partial);
        }
        return new TimeKey(seg, tick, partial, frame);
    }
}
