package dev.dbenton.tasplayback.legacy.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.network.datasync.DataParameter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Entity.class)
public interface EntityAccessor {
    @Accessor("FLAGS") static DataParameter<Byte> tasp$flags() { throw new AssertionError(); }
}
