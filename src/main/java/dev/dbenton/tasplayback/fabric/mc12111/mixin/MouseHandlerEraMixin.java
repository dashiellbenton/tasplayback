package dev.dbenton.tasplayback.fabric.mc12111.mixin;

import dev.dbenton.tasplayback.fabric.mc12111.TaspClient;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Playback: mouse movement must not turn the replayed player. */
@Mixin(MouseHandler.class)
abstract class MouseHandlerEraMixin {
    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void tasp$noTurnInReplay(double d, CallbackInfo ci) {
        if (TaspClient.get().isReplaying()) ci.cancel();
    }
}
