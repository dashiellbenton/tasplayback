package dev.dbenton.tasplayback.fabric.mc12111.mixin;

import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    @Accessor("spinningEffectTime") float tasp$spinTime();
    @Accessor("spinningEffectTime") void tasp$setSpinTime(float f);
    @Accessor("spinningEffectSpeed") float tasp$spinSpeed();
    @Accessor("spinningEffectSpeed") void tasp$setSpinSpeed(float f);
}
