package dev.dbenton.tasplayback.legacy;

import dev.dbenton.tasplayback.core.frame.FrameRecord;
import dev.dbenton.tasplayback.core.time.TimeKey;
import dev.dbenton.tasplayback.legacy.mixin.EntityLivingBaseAccessor;
import dev.dbenton.tasplayback.legacy.mixin.ItemRendererAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.RayTraceResult;
import org.lwjgl.input.Mouse;

import java.util.Arrays;

/** Fills a {@link FrameRecord} from the 1.12.2 client. Reads only. */
final class LegacyFrameCapture {
    private LegacyFrameCapture() {}

    static FrameRecord capture(Minecraft mc, TimeKey key, float partial, float worldFov, float handFov) {
        FrameRecord r = new FrameRecord();
        r.key = key;
        r.wallNanos = System.nanoTime();
        Entity v = mc.getRenderViewEntity();
        if (v != null) {
            r.eyeX = v.prevPosX + (v.posX - v.prevPosX) * partial;
            r.eyeY = v.prevPosY + (v.posY - v.prevPosY) * partial + v.getEyeHeight();
            r.eyeZ = v.prevPosZ + (v.posZ - v.prevPosZ) * partial;
            r.yaw = v.prevRotationYaw + (v.rotationYaw - v.prevRotationYaw) * partial;
            r.pitch = v.prevRotationPitch + (v.rotationPitch - v.prevRotationPitch) * partial;
        }
        r.fov = worldFov; r.handFov = handFov;
        r.perspective = mc.gameSettings.thirdPersonView;
        r.detached = mc.gameSettings.thirdPersonView != 0;
        EntityPlayerSP p = mc.player;
        if (p != null) {
            r.walkDist = p.distanceWalkedModified; r.walkDistO = p.prevDistanceWalkedModified;
            r.bob = p.cameraYaw; r.bobO = p.prevCameraYaw;
            r.hurtTime = p.hurtTime; r.hurtDir = p.attackedAtYaw;
            r.portalIntensity = p.timeInPortal;
            r.attackAnim = p.swingProgress; r.attackAnimO = p.prevSwingProgress;
            r.swingingArm = p.swingingHand == null ? 0 : p.swingingHand.ordinal();
            r.useItemRemaining = ((EntityLivingBaseAccessor) p).tasp$useCount();
            r.usingHand = p.isHandActive() ? p.getActiveHand().ordinal() : -1;
        }
        ItemRendererAccessor ir = (ItemRendererAccessor) mc.getItemRenderer();
        r.mainEquip = ir.tasp$eqMain(); r.mainEquipO = ir.tasp$eqMainO(); r.offEquip = ir.tasp$eqOff(); r.offEquipO = ir.tasp$eqOffO();
        RayTraceResult hit = mc.objectMouseOver;
        if (hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK) {
            r.hitType = FrameRecord.HIT_BLOCK;
            r.hitX = hit.getBlockPos().getX(); r.hitY = hit.getBlockPos().getY(); r.hitZ = hit.getBlockPos().getZ();
            r.hitFace = hit.sideHit.ordinal();
        } else if (hit != null && hit.typeOfHit == RayTraceResult.Type.ENTITY && hit.entityHit != null) {
            r.hitType = FrameRecord.HIT_ENTITY;
            r.hitEntity = hit.entityHit.getEntityId();
        }
        // LWJGL2: integer cursor position, origin bottom-left. Store window coordinates with origin top-left.
        int mx = Mouse.getX(), my = Mouse.getY();
        r.mouseX = mx; r.mouseY = mc.displayHeight - my - 1;
        ScaledResolution sr = new ScaledResolution(mc);
        r.guiMouseX = (double) mx * sr.getScaledWidth() / mc.displayWidth;
        r.guiMouseY = sr.getScaledHeight() - (double) my * sr.getScaledHeight() / mc.displayHeight - 1;
        r.mouseButtons = (Mouse.isButtonDown(0) ? 1 : 0) | (Mouse.isButtonDown(1) ? 2 : 0) | (Mouse.isButtonDown(2) ? 4 : 0);
        int flags = 0;
        if (Mouse.isGrabbed()) flags |= FrameRecord.FLAG_MOUSE_GRABBED;
        if (mc.currentScreen != null) flags |= FrameRecord.FLAG_SCREEN_OPEN;
        if (mc.gameSettings.hideGUI) flags |= FrameRecord.FLAG_HIDE_GUI;
        if (mc.isGamePaused()) flags |= FrameRecord.FLAG_PAUSED;
        if (mc.gameSettings.showDebugInfo) flags |= FrameRecord.FLAG_DEBUG_OVERLAY;
        r.flags = flags;
        KeyBinding[] keys = mc.gameSettings.keyBindings;
        int[] held = new int[keys.length];
        int n = 0;
        for (int i = 0; i < keys.length; i++) if (keys[i].isKeyDown()) held[n++] = i;
        r.keysHeld = Arrays.copyOf(held, n);
        r.windowW = mc.displayWidth; r.windowH = mc.displayHeight;
        r.framebufferW = mc.displayWidth; r.framebufferH = mc.displayHeight;
        r.guiScale = sr.getScaleFactor();
        return r;
    }
}
