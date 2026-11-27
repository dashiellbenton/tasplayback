package dev.dbenton.tasplayback.fabric.mc12111;

import dev.dbenton.tasplayback.core.frame.FrameRecord;
import dev.dbenton.tasplayback.core.time.TimeKey;
import dev.dbenton.tasplayback.fabric.mc12111.mixin.ClientAvatarStateAccessor;
import dev.dbenton.tasplayback.fabric.mc12111.mixin.ItemInHandRendererAccessor;
import dev.dbenton.tasplayback.fabric.mc12111.mixin.LivingEntityAccessor;
import dev.dbenton.tasplayback.fabric.mc12111.mixin.MouseHandlerAccessor;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Arrays;

/** Fills a {@link FrameRecord} from the state the renderer just used. Reads only. */
final class FrameCapture {
    private FrameCapture() {}

    static FrameRecord capture(Minecraft mc, TimeKey key, float worldFov, float handFov) {
        FrameRecord r = new FrameRecord();
        r.key = key;
        r.wallNanos = System.nanoTime();
        Camera cam = mc.gameRenderer.getMainCamera();
        r.eyeX = cam.position().x; r.eyeY = cam.position().y; r.eyeZ = cam.position().z;
        r.yaw = cam.yRot(); r.pitch = cam.xRot(); r.roll = 0f;
        r.fov = worldFov; r.handFov = handFov;
        r.perspective = mc.options.getCameraType().ordinal();
        r.detached = cam.isDetached();
        LocalPlayer p = mc.player;
        if (p != null) {
            ClientAvatarStateAccessor av = (ClientAvatarStateAccessor) p.avatarState();
            r.walkDist = av.tasp$walkDist(); r.walkDistO = av.tasp$walkDistO(); r.bob = av.tasp$bob(); r.bobO = av.tasp$bobO();
            r.hurtTime = p.hurtTime; r.hurtDir = p.getHurtDir();
            r.portalIntensity = p.portalEffectIntensity;
            r.attackAnim = p.attackAnim; r.attackAnimO = p.oAttackAnim;
            r.swingingArm = p.swingingArm == null ? 0 : p.swingingArm.ordinal();
            r.useItemRemaining = ((LivingEntityAccessor) p).tasp$useItemRemaining();
            r.usingHand = p.isUsingItem() ? p.getUsedItemHand().ordinal() : -1;
        }
        ItemInHandRendererAccessor ih = (ItemInHandRendererAccessor) mc.gameRenderer.itemInHandRenderer;
        r.mainEquip = ih.tasp$main(); r.mainEquipO = ih.tasp$mainO(); r.offEquip = ih.tasp$off(); r.offEquipO = ih.tasp$offO();
        HitResult hit = mc.hitResult;
        if (hit instanceof BlockHitResult && hit.getType() == HitResult.Type.BLOCK) {
            BlockHitResult b = (BlockHitResult) hit;
            r.hitType = FrameRecord.HIT_BLOCK;
            r.hitX = b.getBlockPos().getX(); r.hitY = b.getBlockPos().getY(); r.hitZ = b.getBlockPos().getZ();
            r.hitFace = b.getDirection().ordinal();
        } else if (hit instanceof EntityHitResult) {
            r.hitType = FrameRecord.HIT_ENTITY;
            r.hitEntity = ((EntityHitResult) hit).getEntity().getId();
        }
        Window w = mc.getWindow();
        MouseHandlerAccessor m = (MouseHandlerAccessor) mc.mouseHandler;
        r.mouseX = m.tasp$getX(); r.mouseY = m.tasp$getY();
        r.guiMouseX = mc.mouseHandler.getScaledXPos(w); r.guiMouseY = mc.mouseHandler.getScaledYPos(w);
        r.mouseButtons = (m.tasp$left() ? 1 : 0) | (m.tasp$right() ? 2 : 0) | (m.tasp$middle() ? 4 : 0);
        int flags = 0;
        if (mc.mouseHandler.isMouseGrabbed()) flags |= FrameRecord.FLAG_MOUSE_GRABBED;
        if (mc.screen != null) flags |= FrameRecord.FLAG_SCREEN_OPEN;
        if (mc.options.hideGui) flags |= FrameRecord.FLAG_HIDE_GUI;
        if (mc.isPaused()) flags |= FrameRecord.FLAG_PAUSED;
        if (mc.getDebugOverlay().showDebugScreen()) flags |= FrameRecord.FLAG_DEBUG_OVERLAY;
        r.flags = flags;
        KeyMapping[] keys = mc.options.keyMappings;
        int[] held = new int[keys.length];
        int n = 0;
        for (int i = 0; i < keys.length; i++) if (keys[i].isDown()) held[n++] = i;
        r.keysHeld = Arrays.copyOf(held, n);
        r.windowW = w.getScreenWidth(); r.windowH = w.getScreenHeight();
        r.framebufferW = w.getWidth(); r.framebufferH = w.getHeight();
        r.guiScale = w.getGuiScale();
        return r;
    }
}
