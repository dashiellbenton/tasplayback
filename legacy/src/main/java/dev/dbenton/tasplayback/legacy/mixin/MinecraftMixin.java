package dev.dbenton.tasplayback.legacy.mixin;

import dev.dbenton.tasplayback.legacy.TASPlaybackLegacy;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** M0 smoke mixin: proves the mixin + refmap pipeline on both 1.12.2 loaders. Read-only. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void tasplayback$constructed(CallbackInfo ci) {
        TASPlaybackLegacy.LOGGER.info("TASPlayback mixins active (Minecraft constructed)");
    }
}
