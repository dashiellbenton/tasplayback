package dev.dbenton.tasplayback.core.event;

import dev.dbenton.tasplayback.core.format.StreamCodec;
import dev.dbenton.tasplayback.core.io.ByteSink;
import dev.dbenton.tasplayback.core.io.ByteSource;
import dev.dbenton.tasplayback.core.time.TimeKey;
import dev.dbenton.tasplayback.core.time.TimeKeyCodec;

/** Codec shared by all event streams: delta-coded key, varint type, length-prefixed payload. */
public final class EventCodec implements StreamCodec<EventRecord> {
    private TimeKey prev = TimeKeyCodec.ORIGIN;

    @Override public void beginChunk() { prev = TimeKeyCodec.ORIGIN; }

    @Override public TimeKey keyOf(EventRecord r) { return r.key; }

    @Override public void encode(EventRecord r, ByteSink out) {
        TimeKeyCodec.writeDelta(out, r.key, prev);
        out.writeVarInt(r.type);
        out.writeByteArray(r.payload);
        prev = r.key;
    }

    @Override public EventRecord decode(ByteSource in) {
        TimeKey k = TimeKeyCodec.readDelta(in, prev);
        int type = in.readVarInt();
        if (type < 0) throw new dev.dbenton.tasplayback.core.io.FormatException("negative event type " + type);
        byte[] payload = in.readByteArray();
        prev = k;
        return new EventRecord(k, type, payload);
    }
}
