package dev.dbenton.tasplayback.legacy.mixin;

import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemRenderer.class)
public interface ItemRendererAccessor {
    @Accessor("itemStackMainHand") ItemStack tasp$main();
    @Accessor("itemStackMainHand") void tasp$setMain(ItemStack s);
    @Accessor("itemStackOffHand") ItemStack tasp$off();
    @Accessor("itemStackOffHand") void tasp$setOff(ItemStack s);
    @Accessor("equippedProgressMainHand") float tasp$eqMain();
    @Accessor("equippedProgressMainHand") void tasp$setEqMain(float f);
    @Accessor("prevEquippedProgressMainHand") float tasp$eqMainO();
    @Accessor("prevEquippedProgressMainHand") void tasp$setEqMainO(float f);
    @Accessor("equippedProgressOffHand") float tasp$eqOff();
    @Accessor("equippedProgressOffHand") void tasp$setEqOff(float f);
    @Accessor("prevEquippedProgressOffHand") float tasp$eqOffO();
    @Accessor("prevEquippedProgressOffHand") void tasp$setEqOffO(float f);
}
