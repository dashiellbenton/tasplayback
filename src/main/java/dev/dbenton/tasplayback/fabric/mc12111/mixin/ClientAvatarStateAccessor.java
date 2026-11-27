package dev.dbenton.tasplayback.fabric.mc12111.mixin;

import net.minecraft.client.entity.ClientAvatarState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientAvatarState.class)
public interface ClientAvatarStateAccessor {
    @Accessor("walkDist") float tasp$walkDist();
    @Accessor("walkDist") void tasp$setWalkDist(float f);
    @Accessor("walkDistO") float tasp$walkDistO();
    @Accessor("walkDistO") void tasp$setWalkDistO(float f);
    @Accessor("bob") float tasp$bob();
    @Accessor("bob") void tasp$setBob(float f);
    @Accessor("bobO") float tasp$bobO();
    @Accessor("bobO") void tasp$setBobO(float f);
}
