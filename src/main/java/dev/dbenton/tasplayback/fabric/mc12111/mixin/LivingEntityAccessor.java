package dev.dbenton.tasplayback.fabric.mc12111.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Accessor("useItem") void tasp$setUseItem(ItemStack s);
    @Accessor("useItemRemaining") int tasp$useItemRemaining();
    @Accessor("useItemRemaining") void tasp$setUseItemRemaining(int i);
    @Accessor("attackStrengthTicker") int tasp$attackStrengthTicker();
    @Accessor("attackStrengthTicker") void tasp$setAttackStrengthTicker(int i);
    @Accessor("swimAmount") float tasp$swim();
    @Accessor("swimAmount") void tasp$setSwim(float f);
    @Accessor("swimAmountO") float tasp$swimO();
    @Accessor("swimAmountO") void tasp$setSwimO(float f);
    @Accessor("DATA_LIVING_ENTITY_FLAGS")
    static net.minecraft.network.syncher.EntityDataAccessor<Byte> tasp$livingFlagsId() { throw new AssertionError(); }
}
