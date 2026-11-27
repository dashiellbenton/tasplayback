package dev.dbenton.tasplayback.fabric.mc12111.mixin;

import dev.dbenton.tasplayback.fabric.mc12111.ReplayListScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds a "TAS Replays" button to the title screen. */
@Mixin(TitleScreen.class)
abstract class TitleScreenEraMixin extends Screen {
    private TitleScreenEraMixin(Component title) { super(title); }

    @Inject(method = "init", at = @At("TAIL"))
    private void tasp$addReplayButton(CallbackInfo ci) {
        addRenderableWidget(Button.builder(Component.literal("TAS Replays"),
                b -> minecraft.setScreen(new ReplayListScreen((Screen) (Object) this))).bounds(4, 4, 90, 20).build());
    }
}
