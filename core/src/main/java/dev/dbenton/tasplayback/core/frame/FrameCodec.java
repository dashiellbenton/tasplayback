package dev.dbenton.tasplayback.core.frame;

import dev.dbenton.tasplayback.core.format.StreamCodec;
import dev.dbenton.tasplayback.core.io.ByteSink;
import dev.dbenton.tasplayback.core.io.ByteSource;
import dev.dbenton.tasplayback.core.io.FormatException;
import dev.dbenton.tasplayback.core.time.TimeKey;
import dev.dbenton.tasplayback.core.time.TimeKeyCodec;

import java.util.Arrays;

/**
 * Lossless delta codec for {@link FrameRecord} (FRAMES stream, version 1).
 *
 * <p>Every field is coded against the previous frame in the same chunk (the first frame of a chunk is
 * coded against an all-zero record, i.e. it is a keyframe): integers as zigzag varint deltas, floats and
 * doubles as varints of the XOR of their raw IEEE bits. Unchanged values cost one byte; values are
 * reproduced bit-for-bit.
 */
public final class FrameCodec implements StreamCodec<FrameRecord> {
    private static final int MAX_KEYS = 512;
    private static final int MAX_EXT = 1 << 16;

    private FrameRecord prev = blank();

    private static FrameRecord blank() {
        FrameRecord r = new FrameRecord();
        r.key = TimeKeyCodec.ORIGIN;
        r.usingHand = 0; r.mainItemRef = 0; r.offItemRef = 0; r.breakProgress = 0f;
        return r;
    }

    @Override public void beginChunk() { prev = blank(); }

    @Override public TimeKey keyOf(FrameRecord r) { return r.key; }

    @Override public void encode(FrameRecord r, ByteSink o) {
        FrameRecord p = prev;
        TimeKeyCodec.writeDelta(o, r.key, p.key);
        l(o, r.wallNanos, p.wallNanos);
        d(o, r.eyeX, p.eyeX); d(o, r.eyeY, p.eyeY); d(o, r.eyeZ, p.eyeZ);
        f(o, r.yaw, p.yaw); f(o, r.pitch, p.pitch); f(o, r.roll, p.roll);
        f(o, r.fov, p.fov); f(o, r.fovModifier, p.fovModifier);
        f(o, r.walkDist, p.walkDist); f(o, r.walkDistO, p.walkDistO); f(o, r.bob, p.bob); f(o, r.bobO, p.bobO);
        f(o, r.hurtTime, p.hurtTime); f(o, r.hurtDir, p.hurtDir);
        f(o, r.portalIntensity, p.portalIntensity); f(o, r.nauseaIntensity, p.nauseaIntensity);
        i(o, r.perspective, p.perspective); o.writeBoolean(r.detached);
        f(o, r.attackAnim, p.attackAnim); f(o, r.attackAnimO, p.attackAnimO); i(o, r.swingingArm, p.swingingArm);
        f(o, r.mainEquip, p.mainEquip); f(o, r.mainEquipO, p.mainEquipO);
        f(o, r.offEquip, p.offEquip); f(o, r.offEquipO, p.offEquipO);
        i(o, r.useItemRemaining, p.useItemRemaining); i(o, r.usingHand, p.usingHand);
        i(o, r.mainItemRef, p.mainItemRef); i(o, r.offItemRef, p.offItemRef);
        i(o, r.hitType, p.hitType); i(o, r.hitX, p.hitX); i(o, r.hitY, p.hitY); i(o, r.hitZ, p.hitZ);
        i(o, r.hitFace, p.hitFace); i(o, r.hitEntity, p.hitEntity); f(o, r.breakProgress, p.breakProgress);
        d(o, r.mouseX, p.mouseX); d(o, r.mouseY, p.mouseY); d(o, r.guiMouseX, p.guiMouseX); d(o, r.guiMouseY, p.guiMouseY);
        i(o, r.mouseButtons, p.mouseButtons);
        if (Arrays.equals(r.keysHeld, p.keysHeld)) {
            o.writeVarInt(0);
        } else {
            if (r.keysHeld.length > MAX_KEYS) throw new IllegalArgumentException("too many held keys");
            o.writeVarInt(r.keysHeld.length + 1);
            int last = 0;
            for (int k : r.keysHeld) { o.writeZigZagInt(k - last); last = k; }
        }
        i(o, r.windowW, p.windowW); i(o, r.windowH, p.windowH);
        i(o, r.framebufferW, p.framebufferW); i(o, r.framebufferH, p.framebufferH);
        i(o, r.guiScale, p.guiScale); l(o, r.framebufferChecksum, p.framebufferChecksum);
        if (Arrays.equals(r.ext, p.ext)) {
            o.writeVarInt(0);
        } else {
            if (r.ext.length > MAX_EXT) throw new IllegalArgumentException("ext blob too large");
            o.writeVarInt(r.ext.length + 1);
            o.writeBytes(r.ext);
        }
        prev = r.copy();
    }

    @Override public FrameRecord decode(ByteSource in) {
        FrameRecord p = prev;
        FrameRecord r = new FrameRecord();
        r.key = TimeKeyCodec.readDelta(in, p.key);
        r.wallNanos = l(in, p.wallNanos);
        r.eyeX = d(in, p.eyeX); r.eyeY = d(in, p.eyeY); r.eyeZ = d(in, p.eyeZ);
        r.yaw = f(in, p.yaw); r.pitch = f(in, p.pitch); r.roll = f(in, p.roll);
        r.fov = f(in, p.fov); r.fovModifier = f(in, p.fovModifier);
        r.walkDist = f(in, p.walkDist); r.walkDistO = f(in, p.walkDistO); r.bob = f(in, p.bob); r.bobO = f(in, p.bobO);
        r.hurtTime = f(in, p.hurtTime); r.hurtDir = f(in, p.hurtDir);
        r.portalIntensity = f(in, p.portalIntensity); r.nauseaIntensity = f(in, p.nauseaIntensity);
        r.perspective = i(in, p.perspective); r.detached = in.readBoolean();
        r.attackAnim = f(in, p.attackAnim); r.attackAnimO = f(in, p.attackAnimO); r.swingingArm = i(in, p.swingingArm);
        r.mainEquip = f(in, p.mainEquip); r.mainEquipO = f(in, p.mainEquipO);
        r.offEquip = f(in, p.offEquip); r.offEquipO = f(in, p.offEquipO);
        r.useItemRemaining = i(in, p.useItemRemaining); r.usingHand = i(in, p.usingHand);
        r.mainItemRef = i(in, p.mainItemRef); r.offItemRef = i(in, p.offItemRef);
        r.hitType = i(in, p.hitType); r.hitX = i(in, p.hitX); r.hitY = i(in, p.hitY); r.hitZ = i(in, p.hitZ);
        r.hitFace = i(in, p.hitFace); r.hitEntity = i(in, p.hitEntity); r.breakProgress = f(in, p.breakProgress);
        r.mouseX = d(in, p.mouseX); r.mouseY = d(in, p.mouseY); r.guiMouseX = d(in, p.guiMouseX); r.guiMouseY = d(in, p.guiMouseY);
        r.mouseButtons = i(in, p.mouseButtons);
        int nk = in.readVarInt();
        if (nk == 0) {
            r.keysHeld = p.keysHeld.clone();
        } else {
            nk -= 1;
            if (nk > MAX_KEYS) throw new FormatException("too many held keys: " + nk);
            r.keysHeld = new int[nk];
            int last = 0;
            for (int k = 0; k < nk; k++) { last += in.readZigZagInt(); r.keysHeld[k] = last; }
        }
        r.windowW = i(in, p.windowW); r.windowH = i(in, p.windowH);
        r.framebufferW = i(in, p.framebufferW); r.framebufferH = i(in, p.framebufferH);
        r.guiScale = i(in, p.guiScale); r.framebufferChecksum = l(in, p.framebufferChecksum);
        int ne = in.readVarInt();
        if (ne == 0) {
            r.ext = p.ext.clone();
        } else {
            ne -= 1;
            if (ne > MAX_EXT) throw new FormatException("ext blob too large: " + ne);
            r.ext = in.readBytes(ne);
        }
        prev = r.copy();
        return r;
    }

    private static void i(ByteSink o, int v, int p) { o.writeZigZagInt(v - p); }
    private static void l(ByteSink o, long v, long p) { o.writeZigZagLong(v - p); }
    private static void f(ByteSink o, float v, float p) { o.writeVarInt(Float.floatToRawIntBits(v) ^ Float.floatToRawIntBits(p)); }
    private static void d(ByteSink o, double v, double p) { o.writeVarLong(Double.doubleToRawLongBits(v) ^ Double.doubleToRawLongBits(p)); }

    private static int i(ByteSource in, int p) { return p + in.readZigZagInt(); }
    private static long l(ByteSource in, long p) { return p + in.readZigZagLong(); }
    private static float f(ByteSource in, float p) { return Float.intBitsToFloat(in.readVarInt() ^ Float.floatToRawIntBits(p)); }
    private static double d(ByteSource in, double p) { return Double.longBitsToDouble(in.readVarLong() ^ Double.doubleToRawLongBits(p)); }
}
