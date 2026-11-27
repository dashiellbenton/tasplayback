package dev.dbenton.tasplayback.legacy.mixin;

import dev.dbenton.tasplayback.legacy.LegacyGui;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The replay client has no integrated server: show the pause menu buttons as they were recorded. */
@Mixin(GuiIngameMenu.class)
public abstract class GuiIngameMenuLegacyMixin {
    @Inject(method = "initGui", at = @At("RETURN"))
    private void tasp$recordedButtons(CallbackInfo ci) {
        LegacyGui.adjustPauseMenu((GuiScreen) (Object) this);
    }
}
