package dev.dbenton.tasplayback.core.event;

import dev.dbenton.tasplayback.core.time.TimeKey;

import java.util.Arrays;

/**
 * A timestamped event in the GUI, HUD, WORLD, SOUNDS or GUICAP stream. {@code type} and {@code payload}
 * are defined per stream: core defines the SOUNDS payloads ({@link dev.dbenton.tasplayback.core.sound.SoundEvent});
 * the others are version-specific and opaque to core.
 */
public final class EventRecord {
    public final TimeKey key;
    public final int type;
    public final byte[] payload;

    public EventRecord(TimeKey key, int type, byte[] payload) {
        if (type < 0) throw new IllegalArgumentException("type must be >= 0");
        this.key = key;
        this.type = type;
        this.payload = payload;
    }

    @Override public boolean equals(Object o) {
        if (!(o instanceof EventRecord)) return false;
        EventRecord e = (EventRecord) o;
        return key.equals(e.key) && type == e.type && Arrays.equals(payload, e.payload);
    }

    @Override public int hashCode() { return key.hashCode() * 31 + type * 17 + Arrays.hashCode(payload); }

    @Override public String toString() { return "Event{" + key + " type=" + type + " " + payload.length + "B}"; }
}
