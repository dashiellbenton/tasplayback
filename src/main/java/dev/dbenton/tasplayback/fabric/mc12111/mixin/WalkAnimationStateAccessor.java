package dev.dbenton.tasplayback.fabric.mc12111.mixin;

import net.minecraft.world.entity.WalkAnimationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WalkAnimationState.class)
public interface WalkAnimationStateAccessor {
    @Accessor("speedOld") float tasp$speedOld();
    @Accessor("speedOld") void tasp$setSpeedOld(float f);
    @Accessor("speed") float tasp$speed();
    @Accessor("speed") void tasp$setSpeed(float f);
    @Accessor("position") float tasp$position();
    @Accessor("position") void tasp$setPosition(float f);
}
