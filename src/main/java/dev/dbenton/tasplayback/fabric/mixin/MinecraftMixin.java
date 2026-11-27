package dev.dbenton.tasplayback.fabric.mixin;

import dev.dbenton.tasplayback.fabric.TASPlaybackFabric;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** M0 smoke mixin: proves the mixin + refmap pipeline on every node. Read-only. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void tasplayback$constructed(CallbackInfo ci) {
        TASPlaybackFabric.LOGGER.info("TASPlayback mixins active (Minecraft constructed)");
    }
}
