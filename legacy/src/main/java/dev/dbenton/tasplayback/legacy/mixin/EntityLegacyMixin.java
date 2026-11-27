package dev.dbenton.tasplayback.legacy.mixin;

import dev.dbenton.tasplayback.legacy.LegacyClient;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Playback: mouse movement must not turn the replayed player. */
@Mixin(Entity.class)
abstract class EntityLegacyMixin {
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void tasp$noTurnInReplay(float yaw, float pitch, CallbackInfo ci) {
        if ((Object) this instanceof EntityPlayerSP && LegacyClient.get().isReplaying()) ci.cancel();
    }
}
