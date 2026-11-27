package dev.dbenton.tasplayback.core.timeline;

import dev.dbenton.tasplayback.core.frame.FrameRecord;

/**
 * Interpolation between two recorded frames for output frames that fall between them (PLAN.md §2.3):
 * continuous camera/hand/mouse values are interpolated (angles along the shortest arc), discrete state
 * uses step semantics (the earlier frame's value).
 */
public final class Interp {
    private Interp() {}

    public static double lerp(double a, double b, double t) { return a + (b - a) * t; }
    public static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    /** Wraps degrees into [-180, 180). */
    public static double wrapDegrees(double d) {
        double w = d % 360.0;
        if (w >= 180.0) w -= 360.0;
        if (w < -180.0) w += 360.0;
        return w;
    }

    /**
     * Angle interpolation along the shortest arc. The result stays continuous with {@code a} (no wrapping
     * of the base value), which matches how Minecraft keeps yaw unbounded.
     */
    public static float lerpAngle(float a, float b, float t) {
        return (float) (a + wrapDegrees((double) b - a) * t);
    }

    /** Interpolated copy of {@code a} towards {@code b}. {@code t == 0} returns an exact copy of {@code a}. */
    public static FrameRecord frames(FrameRecord a, FrameRecord b, float t) {
        FrameRecord r = a.copy();
        if (t <= 0f) return r;
        if (t >= 1f) { r.copyFrom(b); return r; }
        r.eyeX = lerp(a.eyeX, b.eyeX, t); r.eyeY = lerp(a.eyeY, b.eyeY, t); r.eyeZ = lerp(a.eyeZ, b.eyeZ, t);
        r.yaw = lerpAngle(a.yaw, b.yaw, t); r.pitch = lerpAngle(a.pitch, b.pitch, t); r.roll = lerpAngle(a.roll, b.roll, t);
        r.fov = lerp(a.fov, b.fov, t); r.fovModifier = lerp(a.fovModifier, b.fovModifier, t);
        r.walkDist = lerp(a.walkDist, b.walkDist, t); r.walkDistO = lerp(a.walkDistO, b.walkDistO, t);
        r.bob = lerp(a.bob, b.bob, t); r.bobO = lerp(a.bobO, b.bobO, t);
        r.hurtTime = lerp(a.hurtTime, b.hurtTime, t); r.hurtDir = lerpAngle(a.hurtDir, b.hurtDir, t);
        r.portalIntensity = lerp(a.portalIntensity, b.portalIntensity, t);
        r.nauseaIntensity = lerp(a.nauseaIntensity, b.nauseaIntensity, t);
        // swing restarts jump back to 0: only interpolate while the animation moves forward
        if (b.attackAnim >= a.attackAnim) r.attackAnim = lerp(a.attackAnim, b.attackAnim, t);
        r.mainEquip = lerp(a.mainEquip, b.mainEquip, t); r.offEquip = lerp(a.offEquip, b.offEquip, t);
        if (a.breakProgress >= 0 && b.breakProgress >= a.breakProgress) r.breakProgress = lerp(a.breakProgress, b.breakProgress, t);
        r.mouseX = lerp(a.mouseX, b.mouseX, t); r.mouseY = lerp(a.mouseY, b.mouseY, t);
        r.guiMouseX = lerp(a.guiMouseX, b.guiMouseX, t); r.guiMouseY = lerp(a.guiMouseY, b.guiMouseY, t);
        r.wallNanos = (long) lerp((double) a.wallNanos, (double) b.wallNanos, t);
        // everything else (perspective, items, hit result, buttons, keys, window, ext, key) steps: stays = a
        return r;
    }
}
