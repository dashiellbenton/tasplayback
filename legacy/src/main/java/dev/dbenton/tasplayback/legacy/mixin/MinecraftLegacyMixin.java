package dev.dbenton.tasplayback.legacy.mixin;

import dev.dbenton.tasplayback.legacy.LegacyClient;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Timer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class MinecraftLegacyMixin {
    @Shadow @Final private Timer timer;

    @Inject(method = "runTick", at = @At("HEAD"))
    private void tasp$tickStart(CallbackInfo ci) {
        LegacyClient.get().onTickStart();
    }

    @Inject(method = "runTick", at = @At("TAIL"))
    private void tasp$tickEnd(CallbackInfo ci) {
        LegacyClient.get().onTickEnd();
    }

    /** Playback: after the vanilla timer update, the replay decides ticks and the partial tick. */
    @Inject(method = "runGameLoop", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Timer;updateTimer()V", shift = At.Shift.AFTER))
    private void tasp$afterTimer(CallbackInfo ci) {
        LegacyClient c = LegacyClient.get();
        if (c.isReplaying()) c.replayAdvance(timer);
    }

    @Inject(method = "processKeyBinds", at = @At("HEAD"), cancellable = true)
    private void tasp$noKeybindsInReplay(CallbackInfo ci) {
        if (LegacyClient.get().isReplaying()) ci.cancel();
    }
}
