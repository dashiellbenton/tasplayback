package dev.dbenton.tasplayback.fabric.mc12111.mixin;

import dev.dbenton.tasplayback.fabric.mc12111.DebugText;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * F3 text. Recording: the lines as shown (read-only). Playback: the recorded lines replace the viewer's live
 * values (their FPS, memory, server name), so the overlay shows what the TASer saw.
 */
@Mixin(DebugScreenOverlay.class)
abstract class DebugOverlayEraMixin {
    @Inject(method = "renderLines", at = @At("HEAD"))
    private void tasp$lines(GuiGraphics g, List<String> lines, boolean left, CallbackInfo ci) {
        DebugText.onLines(lines, left);
    }
}
