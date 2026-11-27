package dev.dbenton.tasplayback.core.sound;

import dev.dbenton.tasplayback.core.event.EventRecord;
import dev.dbenton.tasplayback.core.io.FormatException;
import dev.dbenton.tasplayback.core.time.TimeKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SoundEventTest {
    @Test
    void playRoundTripsEveryField() {
        SoundEvent e = SoundEvent.play(77, "minecraft:entity.cow.ambient", "minecraft:mob/cow/say3", "neutral", 0.8f, 1.13f, 10.5, 64, -3.25, false);
        e.streaming = true; e.looping = true; e.hasSeed = true; e.seed = -1234567890123L; e.delayTicks = 3;
        e.attenuation = SoundEvent.ATTENUATION_LINEAR; e.attenuationDistance = 24f;
        EventRecord r = e.toRecord(new TimeKey(1, 50, 0f, 900));
        SoundEvent d = SoundEvent.from(r);
        assertEquals(SoundEvent.PLAY, d.type);
        assertEquals(77, d.instanceId);
        assertEquals("minecraft:entity.cow.ambient", d.eventId);
        assertEquals("minecraft:mob/cow/say3", d.soundPath);
        assertEquals("neutral", d.category);
        assertEquals(0.8f, d.volume); assertEquals(1.13f, d.pitch);
        assertEquals(10.5, d.x); assertEquals(64, d.y); assertEquals(-3.25, d.z);
        assertTrue(d.streaming && d.looping && d.hasSeed && !d.relative);
        assertEquals(-1234567890123L, d.seed);
        assertEquals(3, d.delayTicks);
        assertEquals(24f, d.attenuationDistance);
    }

    @Test
    void updateStopAndStopAllRoundTrip() {
        SoundEvent u = SoundEvent.from(SoundEvent.update(5, 0.5f, 0.9f, 1, 2, 3).toRecord(new TimeKey(0, 1, 0f, 1)));
        assertEquals(SoundEvent.UPDATE, u.type);
        assertEquals(5, u.instanceId);
        assertEquals(0.5f, u.volume); assertEquals(3, u.z);
        SoundEvent s = SoundEvent.from(SoundEvent.stop(9).toRecord(new TimeKey(0, 1, 0f, 1)));
        assertEquals(SoundEvent.STOP, s.type);
        assertEquals(9, s.instanceId);
        assertEquals(SoundEvent.STOP_ALL, SoundEvent.from(SoundEvent.stopAll().toRecord(new TimeKey(0, 1, 0f, 1))).type);
    }

    @Test
    void rejectsTrailingBytesAndUnknownTypes() {
        assertThrows(FormatException.class, () -> SoundEvent.decode(SoundEvent.STOP, new byte[] { 1, 0 }));
        assertThrows(FormatException.class, () -> SoundEvent.decode(99, new byte[0]));
    }
}
