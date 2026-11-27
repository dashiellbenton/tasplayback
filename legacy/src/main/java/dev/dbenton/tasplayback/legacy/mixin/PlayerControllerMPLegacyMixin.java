package dev.dbenton.tasplayback.legacy.mixin;

import dev.dbenton.tasplayback.legacy.LegacyClient;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Recording: marks the client's own block actions, whose immediate client-side block changes are recorded. */
@Mixin(PlayerControllerMP.class)
abstract class PlayerControllerMPLegacyMixin {
    @Inject(method = "onPlayerDestroyBlock", at = @At("HEAD"))
    private void tasp$destroyStart(CallbackInfoReturnable<Boolean> cir) { LegacyClient.get().beginLocalAction(); }

    @Inject(method = "onPlayerDestroyBlock", at = @At("RETURN"))
    private void tasp$destroyEnd(CallbackInfoReturnable<Boolean> cir) { LegacyClient.get().endLocalAction(); }

    @Inject(method = "processRightClickBlock", at = @At("HEAD"))
    private void tasp$useStart(CallbackInfoReturnable<?> cir) { LegacyClient.get().beginLocalAction(); }

    @Inject(method = "processRightClickBlock", at = @At("RETURN"))
    private void tasp$useEnd(CallbackInfoReturnable<?> cir) { LegacyClient.get().endLocalAction(); }
}
