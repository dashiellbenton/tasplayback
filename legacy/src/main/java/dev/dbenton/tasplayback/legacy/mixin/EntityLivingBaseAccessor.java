package dev.dbenton.tasplayback.legacy.mixin;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.network.datasync.DataParameter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityLivingBase.class)
public interface EntityLivingBaseAccessor {
    @Accessor("activeItemStack") void tasp$setActiveItemStack(ItemStack s);
    @Accessor("activeItemStackUseCount") int tasp$useCount();
    @Accessor("activeItemStackUseCount") void tasp$setUseCount(int i);
    @Accessor("ticksSinceLastSwing") int tasp$ticksSinceLastSwing();
    @Accessor("ticksSinceLastSwing") void tasp$setTicksSinceLastSwing(int i);
    @Accessor("HAND_STATES") static DataParameter<Byte> tasp$handStates() { throw new AssertionError(); }
}
