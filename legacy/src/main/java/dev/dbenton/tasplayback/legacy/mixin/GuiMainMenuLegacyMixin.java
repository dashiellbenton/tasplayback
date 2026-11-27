package dev.dbenton.tasplayback.legacy.mixin;

import dev.dbenton.tasplayback.legacy.LegacyReplayListScreen;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds a "TAS Replays" button to the main menu. */
@Mixin(GuiMainMenu.class)
abstract class GuiMainMenuLegacyMixin extends GuiScreen {
    private static final int TASP_BUTTON = 0x54415350;

    @Inject(method = "initGui", at = @At("TAIL"))
    private void tasp$addButton(CallbackInfo ci) {
        buttonList.add(new GuiButton(TASP_BUTTON, 4, 4, 90, 20, "TAS Replays"));
    }

    @Inject(method = "actionPerformed", at = @At("HEAD"), cancellable = true)
    private void tasp$onButton(GuiButton button, CallbackInfo ci) {
        if (button.id == TASP_BUTTON) {
            mc.displayGuiScreen(new LegacyReplayListScreen((GuiScreen) (Object) this));
            ci.cancel();
        }
    }
}
