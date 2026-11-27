package dev.dbenton.tasplayback.core.sound;

import dev.dbenton.tasplayback.core.event.EventRecord;
import dev.dbenton.tasplayback.core.io.ByteSink;
import dev.dbenton.tasplayback.core.io.ByteSource;
import dev.dbenton.tasplayback.core.io.FormatException;
import dev.dbenton.tasplayback.core.time.TimeKey;

/**
 * A sound event, captured where the game resolves and plays a sound instance (PLAN.md §4.4).
 * {@link #soundPath} is the variant the game already chose, so playback never re-rolls randomness.
 *
 * <p>Stored as the payload of an {@link EventRecord} in the SOUNDS stream; {@link #type} is the record type.
 */
public final class SoundEvent {
    public static final int PLAY = 1, STOP = 2, UPDATE = 3, STOP_ALL = 4;
    public static final int ATTENUATION_NONE = 0, ATTENUATION_LINEAR = 1;

    public int type;
    /** Recording-unique id of the sound instance (links PLAY, UPDATE and STOP). */
    public long instanceId;

    // PLAY
    /** Sound event id, e.g. "minecraft:entity.cow.ambient". */
    public String eventId;
    /** Resolved variant resource, e.g. "minecraft:mob/cow/say1". */
    public String soundPath;
    /** Sound category / source, e.g. "neutral", "master", "music". */
    public String category;
    public boolean streaming;
    public boolean looping;
    public int attenuation = ATTENUATION_LINEAR;
    /** Distance at which linear attenuation reaches silence (the sound's attenuation distance). */
    public float attenuationDistance = 16f;
    /** Random seed of the instance where the version has one; {@code hasSeed} false otherwise. */
    public long seed;
    public boolean hasSeed;
    /** Delay in ticks before the sound actually starts (as given by the game). */
    public int delayTicks;

    // PLAY and UPDATE
    public float volume = 1f, pitch = 1f;
    public double x, y, z;
    /** True: position is relative to the listener (UI sounds, music). */
    public boolean relative;

    public static SoundEvent play(long id, String eventId, String soundPath, String category, float volume, float pitch,
                                  double x, double y, double z, boolean relative) {
        SoundEvent e = new SoundEvent();
        e.type = PLAY; e.instanceId = id; e.eventId = eventId; e.soundPath = soundPath; e.category = category;
        e.volume = volume; e.pitch = pitch; e.x = x; e.y = y; e.z = z; e.relative = relative;
        return e;
    }

    public static SoundEvent stop(long id) { SoundEvent e = new SoundEvent(); e.type = STOP; e.instanceId = id; return e; }

    public static SoundEvent stopAll() { SoundEvent e = new SoundEvent(); e.type = STOP_ALL; return e; }

    public static SoundEvent update(long id, float volume, float pitch, double x, double y, double z) {
        SoundEvent e = new SoundEvent();
        e.type = UPDATE; e.instanceId = id; e.volume = volume; e.pitch = pitch; e.x = x; e.y = y; e.z = z;
        return e;
    }

    public byte[] encodePayload() {
        ByteSink o = new ByteSink(64);
        switch (type) {
            case PLAY:
                o.writeVarLong(instanceId);
                o.writeString(eventId);
                o.writeString(soundPath);
                o.writeString(category);
                int flags = (streaming ? 1 : 0) | (looping ? 2 : 0) | (relative ? 4 : 0) | (hasSeed ? 8 : 0);
                o.writeByte(flags);
                o.writeByte(attenuation);
                o.writeFloat(attenuationDistance);
                if (hasSeed) o.writeLong(seed);
                o.writeVarInt(delayTicks);
                o.writeFloat(volume).writeFloat(pitch);
                o.writeDouble(x).writeDouble(y).writeDouble(z);
                break;
            case UPDATE:
                o.writeVarLong(instanceId);
                o.writeFloat(volume).writeFloat(pitch);
                o.writeDouble(x).writeDouble(y).writeDouble(z);
                break;
            case STOP:
                o.writeVarLong(instanceId);
                break;
            case STOP_ALL:
                break;
            default:
                throw new IllegalStateException("unknown sound event type " + type);
        }
        return o.toByteArray();
    }

    public EventRecord toRecord(TimeKey key) { return new EventRecord(key, type, encodePayload()); }

    public static SoundEvent decode(int type, byte[] payload) {
        ByteSource in = new ByteSource(payload);
        SoundEvent e = new SoundEvent();
        e.type = type;
        switch (type) {
            case PLAY:
                e.instanceId = in.readVarLong();
                e.eventId = in.readString();
                e.soundPath = in.readString();
                e.category = in.readString();
                int flags = in.readUnsignedByte();
                if ((flags & ~0xF) != 0) throw new FormatException("unknown sound flags " + flags);
                e.streaming = (flags & 1) != 0; e.looping = (flags & 2) != 0;
                e.relative = (flags & 4) != 0; e.hasSeed = (flags & 8) != 0;
                e.attenuation = in.readUnsignedByte();
                e.attenuationDistance = in.readFloat();
                if (e.hasSeed) e.seed = in.readLong();
                e.delayTicks = in.readVarInt();
                e.volume = in.readFloat(); e.pitch = in.readFloat();
                e.x = in.readDouble(); e.y = in.readDouble(); e.z = in.readDouble();
                break;
            case UPDATE:
                e.instanceId = in.readVarLong();
                e.volume = in.readFloat(); e.pitch = in.readFloat();
                e.x = in.readDouble(); e.y = in.readDouble(); e.z = in.readDouble();
                break;
            case STOP:
                e.instanceId = in.readVarLong();
                break;
            case STOP_ALL:
                break;
            default:
                throw new FormatException("unknown sound event type " + type);
        }
        if (in.hasRemaining()) throw new FormatException("trailing bytes in sound event payload");
        return e;
    }

    public static SoundEvent from(EventRecord r) { return decode(r.type, r.payload); }
}
