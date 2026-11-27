package dev.dbenton.tasplayback.fabric.mc12111.mixin;

import dev.dbenton.tasplayback.fabric.mc12111.Tripwire;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;
import net.minecraft.world.level.levelgen.ThreadSafeLegacyRandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Test-only (see {@link Tripwire}): reports randomness drawn while recorder capture code is running. */
@Mixin({ LegacyRandomSource.class, SingleThreadedRandomSource.class, ThreadSafeLegacyRandomSource.class })
abstract class RandomTripwireMixin {
    @Inject(method = "next", at = @At("HEAD"))
    private void tasp$tripwire(int bits, CallbackInfoReturnable<Integer> cir) {
        if (Tripwire.ENABLED) Tripwire.onRandom();
    }
}
