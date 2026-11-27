package dev.dbenton.tasplayback.fabric.mc12111.mixin;

import dev.dbenton.tasplayback.fabric.mc12111.TaspClient;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Playback: the replayed player does not simulate; its state is restored from the recording every tick. */
@Mixin(LocalPlayer.class)
abstract class LocalPlayerEraMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void tasp$noSimulationInReplay(CallbackInfo ci) {
        if (TaspClient.get().isReplaying()) ci.cancel();
    }
}
