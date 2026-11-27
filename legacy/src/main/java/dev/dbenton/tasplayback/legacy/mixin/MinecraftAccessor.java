package dev.dbenton.tasplayback.legacy.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Timer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Accessor("timer") Timer tasp$timer();
    @Accessor("renderPartialTicksPaused") void tasp$setPausedPartial(float f);
}
