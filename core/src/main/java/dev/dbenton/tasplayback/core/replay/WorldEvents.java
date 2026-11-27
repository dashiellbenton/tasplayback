package dev.dbenton.tasplayback.core.replay;

import dev.dbenton.tasplayback.core.event.EventRecord;
import dev.dbenton.tasplayback.core.io.ByteSink;
import dev.dbenton.tasplayback.core.io.ByteSource;
import dev.dbenton.tasplayback.core.io.FormatException;
import dev.dbenton.tasplayback.core.time.TimeKey;

/**
 * Event types of the WORLD stream.
 *
 * <p>Each segment is one client connection. {@link #RAW_FRAME} records hold the connection's inbound
 * packet frames exactly as they crossed the client pipeline, numbered from 0 per connection. A
 * {@link #DELIVER} record marks the moment the client's main thread handled the packet built from frame
 * {@code seq}: on playback, every frame up to {@code seq} must have been fed by then. Packets the client
 * handles on its network thread get no DELIVER of their own and are fed with the next batch, in order.
 */
public final class WorldEvents {
    private WorldEvents() {}

    public static final int RAW_FRAME = 1;
    public static final int DELIVER = 2;
    /**
     * A whole packet, encoded with the game's codec, recorded when the main thread handled it. Used where the
     * singleplayer channel carries packet objects instead of bytes (1.12.2 - 1.20.4). Equivalent to a
     * RAW_FRAME immediately followed by its DELIVER.
     */
    public static final int PACKET = 3;

    public static EventRecord rawFrame(TimeKey key, long seq, byte[] data, int off, int len) {
        ByteSink s = new ByteSink(len + 10);
        s.writeVarLong(seq);
        s.writeBytes(data, off, len);
        return new EventRecord(key, RAW_FRAME, s.toByteArray());
    }

    public static EventRecord deliver(TimeKey key, long seq) {
        return new EventRecord(key, DELIVER, new ByteSink(10).writeVarLong(seq).toByteArray());
    }

    public static EventRecord packet(TimeKey key, byte[] encoded) {
        return new EventRecord(key, PACKET, encoded);
    }

    public static long seqOf(EventRecord e) {
        return new ByteSource(e.payload).readVarLong();
    }

    /** The packet bytes of a RAW_FRAME record. */
    public static byte[] frameBytes(EventRecord e) {
        if (e.type != RAW_FRAME) throw new IllegalArgumentException("not a raw frame");
        ByteSource in = new ByteSource(e.payload);
        in.readVarLong();
        return in.readBytes(in.remaining());
    }

    static void requireDeliver(EventRecord e) {
        if (e.type != DELIVER) throw new FormatException("not a DELIVER record");
    }
}
