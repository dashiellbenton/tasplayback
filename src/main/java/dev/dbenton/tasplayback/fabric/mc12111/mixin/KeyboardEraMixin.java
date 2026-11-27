package dev.dbenton.tasplayback.fabric.mc12111.mixin;

import dev.dbenton.tasplayback.fabric.mc12111.GuiRecorder;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Raw keyboard input: recorded while a screen is open; blocked for the viewer during playback. */
@Mixin(KeyboardHandler.class)
abstract class KeyboardEraMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void tasp$key(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (GuiRecorder.blockLiveInput()) { ci.cancel(); return; }
        GuiRecorder.onKey(event.key(), event.scancode(), event.modifiers(), action);
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void tasp$char(long window, CharacterEvent event, CallbackInfo ci) {
        if (GuiRecorder.blockLiveInput()) { ci.cancel(); return; }
        GuiRecorder.onChar(event.codepoint(), event.modifiers());
    }
}
