package dev.dbenton.tasplayback.fabric.mc12111.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.dbenton.tasplayback.fabric.mc12111.GuiRecorder;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Raw mouse input. Recording: events that reach an open screen are recorded (read-only). Playback: the
 * viewer's own input is blocked; only recorded events are dispatched.
 */
@Mixin(MouseHandler.class)
abstract class InputEraMixin {
    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void tasp$button(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        if (GuiRecorder.blockLiveInput()) { ci.cancel(); return; }
        GuiRecorder.onButton(info.button(), info.modifiers(), action);
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void tasp$scroll(long window, double dx, double dy, CallbackInfo ci) {
        if (GuiRecorder.blockLiveInput()) { ci.cancel(); return; }
        GuiRecorder.onScroll(dx, dy);
    }

    @Inject(method = "onMove", at = @At("HEAD"), cancellable = true)
    private void tasp$move(long window, double x, double y, CallbackInfo ci) {
        if (GuiRecorder.blockLiveInput()) { ci.cancel(); return; }
        GuiRecorder.onMove(x, y);
    }

    /** Double-click detection uses the wall clock; replayed clicks use the recorded time. */
    @ModifyExpressionValue(method = "onButton", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Util;getMillis()J"))
    private long tasp$clickClock(long original) {
        return GuiRecorder.eventMillis(original);
    }
}
