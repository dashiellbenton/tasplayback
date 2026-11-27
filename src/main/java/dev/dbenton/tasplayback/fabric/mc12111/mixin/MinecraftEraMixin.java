package dev.dbenton.tasplayback.fabric.mc12111.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.dbenton.tasplayback.fabric.mc12111.TaspClient;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class MinecraftEraMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void tasp$tickStart(CallbackInfo ci) {
        TaspClient.get().onTickStart();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void tasp$tickEnd(CallbackInfo ci) {
        TaspClient.get().onTickEnd();
    }

    /** Playback: the replay drives ticks and the partial tick from the recording instead of the wall clock. */
    @WrapOperation(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/DeltaTracker$Timer;advanceTime(JZ)I"))
    private int tasp$advanceTime(DeltaTracker.Timer timer, long millis, boolean running, Operation<Integer> original) {
        int vanilla = original.call(timer, millis, running);
        TaspClient c = TaspClient.get();
        return c.isReplaying() ? c.replayAdvance() : vanilla;
    }

    /** Playback: the user's game keys must not act on the replayed player. */
    @Inject(method = "handleKeybinds", at = @At("HEAD"), cancellable = true)
    private void tasp$noKeybindsInReplay(CallbackInfo ci) {
        if (TaspClient.get().isReplaying()) ci.cancel();
    }
}
