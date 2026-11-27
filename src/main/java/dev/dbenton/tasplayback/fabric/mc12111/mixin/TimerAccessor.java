package dev.dbenton.tasplayback.fabric.mc12111.mixin;

import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DeltaTracker.Timer.class)
public interface TimerAccessor {
    @Accessor("deltaTickResidual") void tasp$setResidual(float f);
    @Accessor("pausedDeltaTickResidual") void tasp$setPausedResidual(float f);
    @Accessor("deltaTicks") void tasp$setDeltaTicks(float f);
    @Accessor("lastMs") void tasp$setLastMs(long ms);
}
