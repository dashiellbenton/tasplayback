package dev.dbenton.tasplayback.legacy.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.dbenton.tasplayback.legacy.LegacyGui;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.12.2 screen input. TASmod redirects the LWJGL calls in these methods, so this mixin never touches them:
 * it records the resolved arguments where vanilla hands them to the screen (which also captures TASmod's
 * virtual input), and during playback it replaces the input loop with recorded events.
 */
@Mixin(GuiScreen.class)
abstract class GuiScreenLegacyMixin {
    @WrapOperation(method = "handleMouseInput", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiScreen;mouseClicked(III)V"))
    private void tasp$clicked(GuiScreen self, int x, int y, int button, Operation<Void> op) {
        LegacyGui.onMouse(LegacyGui.CLICK, x, y, button, 0L);
        op.call(self, x, y, button);
    }

    @WrapOperation(method = "handleMouseInput", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiScreen;mouseReleased(III)V"))
    private void tasp$released(GuiScreen self, int x, int y, int state, Operation<Void> op) {
        LegacyGui.onMouse(LegacyGui.RELEASE, x, y, state, 0L);
        op.call(self, x, y, state);
    }

    @WrapOperation(method = "handleMouseInput", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiScreen;mouseClickMove(IIIJ)V"))
    private void tasp$dragged(GuiScreen self, int x, int y, int button, long time, Operation<Void> op) {
        LegacyGui.onMouse(LegacyGui.DRAG, x, y, button, time);
        op.call(self, x, y, button, time);
    }

    @WrapOperation(method = "handleKeyboardInput", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiScreen;keyTyped(CI)V"))
    private void tasp$typed(GuiScreen self, char c, int key, Operation<Void> op) {
        LegacyGui.onKey(c, key);
        op.call(self, c, key);
    }

    /** Playback: the viewer's input queue is not read; recorded events for this tick are dispatched instead. */
    @Inject(method = "handleInput", at = @At("HEAD"), cancellable = true)
    private void tasp$replayInput(CallbackInfo ci) {
        if (LegacyGui.replayInput((GuiScreen) (Object) this)) ci.cancel();
    }

    @Inject(method = "isShiftKeyDown", at = @At("RETURN"), cancellable = true)
    private static void tasp$shift(CallbackInfoReturnable<Boolean> cir) {
        if (LegacyGui.dispatching) cir.setReturnValue(LegacyGui.shift);
    }

    @Inject(method = "isCtrlKeyDown", at = @At("RETURN"), cancellable = true)
    private static void tasp$ctrl(CallbackInfoReturnable<Boolean> cir) {
        if (LegacyGui.dispatching) cir.setReturnValue(LegacyGui.ctrl);
    }

    @Inject(method = "isAltKeyDown", at = @At("RETURN"), cancellable = true)
    private static void tasp$alt(CallbackInfoReturnable<Boolean> cir) {
        if (LegacyGui.dispatching) cir.setReturnValue(LegacyGui.alt);
    }
}
