package dev.dbenton.tasplayback.fabric.mc12111.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.dbenton.tasplayback.fabric.mc12111.TaspClient;
import dev.dbenton.tasplayback.fabric.mc12111.TaspHooks;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
abstract class GameRendererEraMixin {
    /** Recording: remembers the final FOV. Playback: replaces it with the recorded one. */
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void tasp$fov(Camera camera, float partial, boolean world, CallbackInfoReturnable<Float> cir) {
        float v = cir.getReturnValueF();
        float r = TaspClient.get().fov(world, v);
        if (Float.floatToRawIntBits(r) != Float.floatToRawIntBits(v)) cir.setReturnValue(r);
    }

    /** Cursor / status overlay, drawn last into this frame's GUI. */
    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/render/GuiRenderer;render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V"))
    private void tasp$overlay(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci, @Local GuiGraphics graphics) {
        TaspHooks.overlay(graphics);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void tasp$frameDone(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        TaspClient.get().onFrameRendered();
    }
}
