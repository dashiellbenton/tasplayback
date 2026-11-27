package dev.dbenton.tasplayback.core.timeline;

import dev.dbenton.tasplayback.core.format.TestFrames;
import dev.dbenton.tasplayback.core.frame.FrameRecord;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InterpTest {
    @Test
    void anglesTakeTheShortestArc() {
        assertEquals(180f, Math.abs(Interp.lerpAngle(170f, -170f, 0.5f)), 1e-4);
        assertEquals(360f, Interp.lerpAngle(350f, 370f, 0.5f), 1e-4);
        assertEquals(355f, Interp.lerpAngle(350f, 0f, 0.5f), 1e-4, "keeps unbounded yaw continuous");
        assertEquals(-5f, Interp.lerpAngle(-10f, 0f, 0.5f), 1e-6);
        assertEquals(-180.0, Interp.wrapDegrees(180.0), 0);
        assertEquals(-90.0, Interp.wrapDegrees(270.0), 0);
    }

    @Test
    void endpointsAreExactAndDiscreteStateSteps() {
        List<FrameRecord> f = TestFrames.generate(20, 2);
        FrameRecord a = f.get(0), b = f.get(1);
        b.perspective = 2; a.perspective = 0; b.mainItemRef = 9; a.mainItemRef = 1;
        assertTrue(Interp.frames(a, b, 0f).bitEquals(a));
        assertTrue(Interp.frames(a, b, 1f).bitEquals(b));
        FrameRecord m = Interp.frames(a, b, 0.5f);
        assertEquals(0, m.perspective);
        assertEquals(1, m.mainItemRef);
        assertEquals((a.eyeX + b.eyeX) / 2, m.eyeX, 1e-9);
        assertEquals((a.mouseX + b.mouseX) / 2, m.mouseX, 1e-9);
    }

    @Test
    void swingRestartDoesNotInterpolateBackwards() {
        FrameRecord a = new FrameRecord(), b = new FrameRecord();
        a.attackAnim = 0.9f; b.attackAnim = 0.1f;
        assertEquals(0.9f, Interp.frames(a, b, 0.5f).attackAnim);
        a.attackAnim = 0.2f; b.attackAnim = 0.6f;
        assertEquals(0.4f, Interp.frames(a, b, 0.5f).attackAnim, 1e-6);
    }
}
