package dev.dbenton.tasplayback.core.replay;

import dev.dbenton.tasplayback.core.event.EventRecord;
import dev.dbenton.tasplayback.core.time.TimeKey;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KeyedTrackTest {
    static EventRecord e(long tick, long frame, int id) {
        return new EventRecord(new TimeKey(0, tick, 0f, frame), GuiEvents.INPUT, new byte[] { (byte) id });
    }

    @Test
    void releasesByTickAndFrameInRecordingOrder() {
        List<EventRecord> l = new ArrayList<>();
        l.add(e(3, 10, 1));  // input before tick 4, frame 10
        l.add(e(4, 10, 2));  // screen opened during tick 4 (same frame)
        l.add(e(4, 11, 3));  // input before the next frame
        KeyedTrack t = new KeyedTrack(l, x -> true);
        assertEquals(1, t.due(3, 10).size());
        assertEquals(0, t.due(3, 50).size(), "the next event needs tick 4 first");
        assertEquals(1, t.due(4, 10).size());
        assertEquals(0, t.due(4, 10).size());
        assertEquals(1, t.due(4, 11).size());
    }
}
