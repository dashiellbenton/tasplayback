package dev.dbenton.tasplayback.legacy.mixin;

import dev.dbenton.tasplayback.legacy.LegacyClient;
import net.minecraft.client.entity.EntityPlayerSP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Playback: the replayed player does not simulate; its state is restored every tick. */
@Mixin(EntityPlayerSP.class)
abstract class EntityPlayerSPLegacyMixin {
    @Inject(method = "onUpdate", at = @At("HEAD"), cancellable = true)
    private void tasp$noSimulationInReplay(CallbackInfo ci) {
        if (LegacyClient.get().isReplaying()) ci.cancel();
    }
}
