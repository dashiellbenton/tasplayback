package dev.dbenton.tasplayback.fabric.mc12111.mixin;

import dev.dbenton.tasplayback.fabric.mc12111.Tripwire;
import net.minecraft.world.level.levelgen.Xoroshiro128PlusPlus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Xoroshiro128PlusPlus.class)
abstract class XoroshiroTripwireMixin {
    @Inject(method = "nextLong", at = @At("HEAD"))
    private void tasp$tripwire(CallbackInfoReturnable<Long> cir) {
        if (Tripwire.ENABLED) Tripwire.onRandom();
    }
}
