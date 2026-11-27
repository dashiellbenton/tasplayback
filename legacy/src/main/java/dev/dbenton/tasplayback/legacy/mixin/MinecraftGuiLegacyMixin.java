package dev.dbenton.tasplayback.legacy.mixin;

import dev.dbenton.tasplayback.legacy.LegacyClient;
import dev.dbenton.tasplayback.legacy.LegacyGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class MinecraftGuiLegacyMixin {
    @Inject(method = "displayGuiScreen", at = @At("HEAD"))
    private void tasp$screen(GuiScreen screen, CallbackInfo ci) {
        LegacyGui.onDisplayScreen(screen);
    }

    /** Playback: the viewer's in-game keyboard and mouse (F-keys, clicks) must not act on the replay. */
    @Inject(method = "runTickKeyboard", at = @At("HEAD"), cancellable = true)
    private void tasp$noKeyboard(CallbackInfo ci) {
        if (LegacyClient.get().isReplaying()) ci.cancel();
    }

    @Inject(method = "runTickMouse", at = @At("HEAD"), cancellable = true)
    private void tasp$noMouse(CallbackInfo ci) {
        if (LegacyClient.get().isReplaying()) ci.cancel();
    }

    /** Global hotkeys (F2 screenshot, F11 fullscreen) read live LWJGL events: not during replayed key events. */
    @Inject(method = "dispatchKeypresses", at = @At("HEAD"), cancellable = true)
    private void tasp$noGlobalKeys(CallbackInfo ci) {
        if (LegacyClient.get().isReplaying()) ci.cancel();
    }
}
