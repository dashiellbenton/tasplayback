package dev.dbenton.tasplayback.fabric.mc12111.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.dbenton.tasplayback.fabric.mc12111.TaspHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Playback: toasts animate on wall-clock time. During replay they read the recorded wall-clock time of the
 * frame being shown, so they appear, slide and disappear exactly as they did while recording.
 */
@Mixin(targets = "net.minecraft.client.gui.components.toasts.ToastManager$ToastInstance")
abstract class ToastInstanceEraMixin {
    @ModifyExpressionValue(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Util;getMillis()J"))
    private long tasp$recordedClock(long original) {
        return TaspHooks.uiMillis(original);
    }
}
