package dev.dbenton.tasplayback.legacy.mixin;

import dev.dbenton.tasplayback.legacy.LegacyGui;
import net.minecraft.client.gui.GuiOverlayDebug;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** F3 text: recorded as shown; during playback the recorded lines replace the viewer's live values. */
@Mixin(GuiOverlayDebug.class)
abstract class GuiOverlayDebugLegacyMixin {
    @Inject(method = "call", at = @At("RETURN"), cancellable = true)
    private void tasp$left(CallbackInfoReturnable<List<String>> cir) {
        List<String> r = LegacyGui.debugLines(cir.getReturnValue(), true);
        if (r != cir.getReturnValue()) cir.setReturnValue(r);
    }

    @Inject(method = "getDebugInfoRight", at = @At("RETURN"), cancellable = true)
    private void tasp$right(CallbackInfoReturnable<List<String>> cir) {
        List<String> r = LegacyGui.debugLines(cir.getReturnValue(), false);
        if (r != cir.getReturnValue()) cir.setReturnValue(r);
    }
}
