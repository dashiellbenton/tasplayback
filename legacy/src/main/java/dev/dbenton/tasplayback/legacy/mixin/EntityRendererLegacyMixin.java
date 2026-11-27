package dev.dbenton.tasplayback.legacy.mixin;

import dev.dbenton.tasplayback.legacy.LegacyClient;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
abstract class EntityRendererLegacyMixin {
    @Inject(method = "getFOVModifier", at = @At("RETURN"), cancellable = true)
    private void tasp$fov(float partialTicks, boolean useFOVSetting, CallbackInfoReturnable<Float> cir) {
        float v = cir.getReturnValueF();
        float r = LegacyClient.get().fov(useFOVSetting, v);
        if (Float.floatToRawIntBits(r) != Float.floatToRawIntBits(v)) cir.setReturnValue(r);
    }

    @Inject(method = "updateCameraAndRender", at = @At("TAIL"))
    private void tasp$frameDone(float partialTicks, long nanoTime, CallbackInfo ci) {
        LegacyClient.get().onFrameRendered(partialTicks);
    }
}
