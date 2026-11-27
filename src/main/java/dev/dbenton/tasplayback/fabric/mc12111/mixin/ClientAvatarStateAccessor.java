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
    @Accessor("xCloak") double tasp$xCloak();
    @Accessor("xCloak") void tasp$setxCloak(double d);
    @Accessor("yCloak") double tasp$yCloak();
    @Accessor("yCloak") void tasp$setyCloak(double d);
    @Accessor("zCloak") double tasp$zCloak();
    @Accessor("zCloak") void tasp$setzCloak(double d);
    @Accessor("xCloakO") double tasp$xCloakO();
    @Accessor("xCloakO") void tasp$setxCloakO(double d);
    @Accessor("yCloakO") double tasp$yCloakO();
    @Accessor("yCloakO") void tasp$setyCloakO(double d);
    @Accessor("zCloakO") double tasp$zCloakO();
    @Accessor("zCloakO") void tasp$setzCloakO(double d);
}
