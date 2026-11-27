package dev.dbenton.tasplayback.core.frame;

import dev.dbenton.tasplayback.core.time.TimeKey;

import java.util.Arrays;

/**
 * Everything captured once per rendered frame (PLAN.md §4.3). {@code eyeX/Y/Z} is the final camera position
 * (in third person: after the camera was pulled back). Version-neutral: values are the final
 * inputs the renderer used that frame, captured read-only. Mutable POJO with public fields so the
 * version layer can fill pooled instances without allocation churn.
 *
 * <p>{@code ext} is an opaque, version-specific blob for camera/first-person inputs that only some
 * versions have (spyglass scope, darkness pulse, freeze overlay, ...). {@code core} stores it verbatim.
 */
public final class FrameRecord {
    public static final int HIT_MISS = 0, HIT_BLOCK = 1, HIT_ENTITY = 2;
    /** {@link #flags} bits. */
    public static final int FLAG_MOUSE_GRABBED = 1, FLAG_SCREEN_OPEN = 2, FLAG_HIDE_GUI = 4, FLAG_PAUSED = 8, FLAG_DEBUG_OVERLAY = 16;
    public static final int NO_ITEM = -1;

    public TimeKey key;
    /** Wall clock (System.nanoTime) when the frame was captured. Metadata only. */
    public long wallNanos;

    // camera
    public double eyeX, eyeY, eyeZ;
    public float yaw, pitch, roll;
    /** Final FOV in degrees fed to the world projection matrix (after FOV effects, underwater, death). */
    public float fov;
    /** Final FOV in degrees used for the first-person hand projection. */
    public float handFov;
    public float walkDist, walkDistO, bob, bobO;
    public float hurtTime, hurtDir;
    public float portalIntensity, nauseaIntensity;
    /** 0 = first person, 1 = third person back, 2 = third person front. */
    public int perspective;
    public boolean detached;

    // first-person hands
    public float attackAnim, attackAnimO;
    /** 0 = main hand, 1 = off hand. */
    public int swingingArm;
    public float mainEquip, mainEquipO, offEquip, offEquipO;
    public int useItemRemaining;
    /** -1 = not using, 0 = main hand, 1 = off hand. */
    public int usingHand = -1;
    /** References into the HUD stream's item table, or {@link #NO_ITEM}. */
    public int mainItemRef = NO_ITEM, offItemRef = NO_ITEM;

    // crosshair target
    public int hitType;
    public int hitX, hitY, hitZ;
    public int hitFace;
    public int hitEntity;
    /** Block-breaking progress in [0,1], or -1 when not breaking. */
    public float breakProgress = -1f;

    // mouse / input
    /** Raw cursor position in window coordinates (sub-pixel). */
    public double mouseX, mouseY;
    /** Cursor position in GUI-scaled coordinates, as passed to screen rendering. */
    public double guiMouseX, guiMouseY;
    /** Bit i = mouse button i held. */
    public int mouseButtons;
    /** FLAG_* bits: cursor grabbed, screen open, GUI hidden (F1), paused, debug overlay. */
    public int flags;
    /** Key codes held this frame (for the input overlay), ascending. */
    public int[] keysHeld = new int[0];

    // window
    public int windowW, windowH, framebufferW, framebufferH;
    public int guiScale;
    /** Optional framebuffer checksum for verification runs (0 = not captured). */
    public long framebufferChecksum;

    /** Version-specific extra inputs, opaque to core. Never null. */
    public byte[] ext = new byte[0];

    public FrameRecord copy() {
        FrameRecord c = new FrameRecord();
        c.copyFrom(this);
        return c;
    }

    public void copyFrom(FrameRecord o) {
        key = o.key; wallNanos = o.wallNanos;
        eyeX = o.eyeX; eyeY = o.eyeY; eyeZ = o.eyeZ;
        yaw = o.yaw; pitch = o.pitch; roll = o.roll; fov = o.fov; handFov = o.handFov;
        walkDist = o.walkDist; walkDistO = o.walkDistO; bob = o.bob; bobO = o.bobO;
        hurtTime = o.hurtTime; hurtDir = o.hurtDir; portalIntensity = o.portalIntensity; nauseaIntensity = o.nauseaIntensity;
        perspective = o.perspective; detached = o.detached;
        attackAnim = o.attackAnim; attackAnimO = o.attackAnimO; swingingArm = o.swingingArm;
        mainEquip = o.mainEquip; mainEquipO = o.mainEquipO; offEquip = o.offEquip; offEquipO = o.offEquipO;
        useItemRemaining = o.useItemRemaining; usingHand = o.usingHand; mainItemRef = o.mainItemRef; offItemRef = o.offItemRef;
        hitType = o.hitType; hitX = o.hitX; hitY = o.hitY; hitZ = o.hitZ; hitFace = o.hitFace; hitEntity = o.hitEntity;
        breakProgress = o.breakProgress;
        mouseX = o.mouseX; mouseY = o.mouseY; guiMouseX = o.guiMouseX; guiMouseY = o.guiMouseY;
        mouseButtons = o.mouseButtons; flags = o.flags; keysHeld = o.keysHeld.clone();
        windowW = o.windowW; windowH = o.windowH; framebufferW = o.framebufferW; framebufferH = o.framebufferH;
        guiScale = o.guiScale; framebufferChecksum = o.framebufferChecksum; ext = o.ext.clone();
    }

    /** Bit-exact equality over all fields (floats compared by raw bits). */
    public boolean bitEquals(FrameRecord o) {
        return equalsKey(key, o.key) && wallNanos == o.wallNanos
                && d(eyeX, o.eyeX) && d(eyeY, o.eyeY) && d(eyeZ, o.eyeZ)
                && f(yaw, o.yaw) && f(pitch, o.pitch) && f(roll, o.roll) && f(fov, o.fov) && f(handFov, o.handFov)
                && f(walkDist, o.walkDist) && f(walkDistO, o.walkDistO) && f(bob, o.bob) && f(bobO, o.bobO)
                && f(hurtTime, o.hurtTime) && f(hurtDir, o.hurtDir) && f(portalIntensity, o.portalIntensity)
                && f(nauseaIntensity, o.nauseaIntensity) && perspective == o.perspective && detached == o.detached
                && f(attackAnim, o.attackAnim) && f(attackAnimO, o.attackAnimO) && swingingArm == o.swingingArm
                && f(mainEquip, o.mainEquip) && f(mainEquipO, o.mainEquipO) && f(offEquip, o.offEquip) && f(offEquipO, o.offEquipO)
                && useItemRemaining == o.useItemRemaining && usingHand == o.usingHand
                && mainItemRef == o.mainItemRef && offItemRef == o.offItemRef
                && hitType == o.hitType && hitX == o.hitX && hitY == o.hitY && hitZ == o.hitZ && hitFace == o.hitFace
                && hitEntity == o.hitEntity && f(breakProgress, o.breakProgress)
                && d(mouseX, o.mouseX) && d(mouseY, o.mouseY) && d(guiMouseX, o.guiMouseX) && d(guiMouseY, o.guiMouseY)
                && mouseButtons == o.mouseButtons && flags == o.flags && Arrays.equals(keysHeld, o.keysHeld)
                && windowW == o.windowW && windowH == o.windowH && framebufferW == o.framebufferW && framebufferH == o.framebufferH
                && guiScale == o.guiScale && framebufferChecksum == o.framebufferChecksum && Arrays.equals(ext, o.ext);
    }

    private static boolean equalsKey(TimeKey a, TimeKey b) { return a == null ? b == null : a.equals(b); }
    private static boolean f(float a, float b) { return Float.floatToRawIntBits(a) == Float.floatToRawIntBits(b); }
    private static boolean d(double a, double b) { return Double.doubleToRawLongBits(a) == Double.doubleToRawLongBits(b); }
}
