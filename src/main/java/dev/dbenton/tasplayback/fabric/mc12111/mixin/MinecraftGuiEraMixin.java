package dev.dbenton.tasplayback.fabric.mc12111.mixin;

import dev.dbenton.tasplayback.fabric.mc12111.GuiRecorder;
import dev.dbenton.tasplayback.fabric.mc12111.TaspClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
abstract class MinecraftGuiEraMixin {
    /** Recording: which screen the client switches to. */
    @Inject(method = "setScreen", at = @At("HEAD"))
    private void tasp$setScreen(Screen screen, CallbackInfo ci) {
        GuiRecorder.onSetScreen(screen);
    }

    /** Playback: replayed drags and focus-dependent behaviour must not depend on the viewer's window focus. */
    @Inject(method = "isWindowActive", at = @At("HEAD"), cancellable = true)
    private void tasp$activeInReplay(CallbackInfoReturnable<Boolean> cir) {
        if (TaspClient.get().isReplaying()) cir.setReturnValue(true);
    }
}
