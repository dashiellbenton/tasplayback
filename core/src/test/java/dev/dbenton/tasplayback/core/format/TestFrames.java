package dev.dbenton.tasplayback.core.format;

import dev.dbenton.tasplayback.core.frame.FrameRecord;
import dev.dbenton.tasplayback.core.time.TimeKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Deterministic pseudo-random frame sequences that look like a real low-tickrate recording. */
public final class TestFrames {
    private TestFrames() {}

    public static List<FrameRecord> generate(long seed, int count) {
        Random r = new Random(seed);
        List<FrameRecord> out = new ArrayList<>();
        long tick = 100;
        float partial = 0f;
        float yaw = 0f, pitch = 0f;
        for (int i = 0; i < count; i++) {
            // low tickrate: many frames per tick, occasionally frozen (same partial repeated)
            if (r.nextInt(10) != 0) {
                partial += 0.013f + r.nextFloat() * 0.05f;
                if (partial >= 1f) { partial -= 1f; tick++; if (partial >= 1f) partial = 0.999f; }
            }
            yaw += (r.nextFloat() - 0.5f) * 7f;
            pitch = Math.max(-90f, Math.min(90f, pitch + (r.nextFloat() - 0.5f) * 3f));
            FrameRecord f = new FrameRecord();
            f.key = new TimeKey(0, tick, partial, i);
            f.wallNanos = 1_000_000_000L + i * 16_666_667L + r.nextInt(100_000);
            f.eyeX = 100.5 + tick * 0.1 + partial * 0.1; f.eyeY = 64.62; f.eyeZ = -200.25 - r.nextDouble() * 1e-9;
            f.yaw = yaw; f.pitch = pitch; f.roll = 0f;
            f.fov = 70f + (r.nextInt(50) == 0 ? 10f : 0f); f.fovModifier = 1.0f + r.nextFloat() * 0.15f;
            f.walkDist = tick * 0.4f; f.walkDistO = f.walkDist - 0.4f; f.bob = r.nextFloat() * 0.1f; f.bobO = f.bob * 0.9f;
            f.hurtTime = r.nextInt(30) == 0 ? 10f : 0f; f.hurtDir = r.nextFloat() * 360f;
            f.portalIntensity = 0f; f.nauseaIntensity = r.nextInt(100) == 0 ? 0.5f : 0f;
            f.perspective = r.nextInt(200) == 0 ? 1 : 0; f.detached = f.perspective != 0;
            f.attackAnim = r.nextFloat(); f.attackAnimO = f.attackAnim * 0.8f; f.swingingArm = r.nextInt(2);
            f.mainEquip = 1f; f.mainEquipO = 1f; f.offEquip = r.nextFloat(); f.offEquipO = f.offEquip;
            f.useItemRemaining = r.nextInt(5) == 0 ? 72000 - i : 0; f.usingHand = f.useItemRemaining > 0 ? 0 : -1;
            f.mainItemRef = r.nextInt(4); f.offItemRef = FrameRecord.NO_ITEM;
            f.hitType = r.nextInt(3); f.hitX = 100 + r.nextInt(3); f.hitY = 63; f.hitZ = -201; f.hitFace = r.nextInt(6); f.hitEntity = r.nextInt(1000);
            f.breakProgress = r.nextInt(4) == 0 ? r.nextFloat() : -1f;
            f.mouseX = 427.0 + r.nextGaussian() * 30; f.mouseY = 240.0 + r.nextGaussian() * 30;
            f.guiMouseX = f.mouseX / 2; f.guiMouseY = f.mouseY / 2;
            f.mouseButtons = r.nextInt(8) == 0 ? 1 : 0;
            f.keysHeld = r.nextInt(5) == 0 ? new int[] { 32, 87 } : (r.nextInt(3) == 0 ? new int[] { 87 } : new int[0]);
            f.windowW = 854; f.windowH = 480; f.framebufferW = 854; f.framebufferH = 480; f.guiScale = 2;
            f.framebufferChecksum = r.nextInt(10) == 0 ? r.nextLong() : 0;
            f.ext = r.nextInt(50) == 0 ? new byte[] { (byte) r.nextInt(), 7 } : (i == 0 ? new byte[0] : out.get(i - 1).ext.clone());
            out.add(f);
        }
        return out;
    }
}
