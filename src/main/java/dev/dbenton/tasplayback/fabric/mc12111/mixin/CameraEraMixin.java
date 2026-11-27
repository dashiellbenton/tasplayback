package dev.dbenton.tasplayback.fabric.mc12111.mixin;

import dev.dbenton.tasplayback.fabric.mc12111.TaspHooks;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Playback: the final camera pose comes from the recording (bit-exact on recorded frames). */
@Mixin(Camera.class)
abstract class CameraEraMixin {
    @Inject(method = "setup", at = @At("TAIL"))
    private void tasp$overrideCamera(Level level, Entity entity, boolean detached, boolean mirrored, float partial, CallbackInfo ci) {
        TaspHooks.overrideCamera((Camera) (Object) this);
    }
}
