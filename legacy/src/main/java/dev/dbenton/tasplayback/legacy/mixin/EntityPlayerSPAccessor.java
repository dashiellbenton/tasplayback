package dev.dbenton.tasplayback.legacy.mixin;

import net.minecraft.client.entity.EntityPlayerSP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityPlayerSP.class)
public interface EntityPlayerSPAccessor {
    @Accessor("handActive") boolean tasp$handActive();
    @Accessor("handActive") void tasp$setHandActive(boolean b);
}
