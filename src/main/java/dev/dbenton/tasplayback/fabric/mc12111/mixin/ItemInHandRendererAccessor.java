package dev.dbenton.tasplayback.fabric.mc12111.mixin;

import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemInHandRenderer.class)
public interface ItemInHandRendererAccessor {
    @Accessor("mainHandItem") ItemStack tasp$mainItem();
    @Accessor("mainHandItem") void tasp$setMainItem(ItemStack s);
    @Accessor("offHandItem") ItemStack tasp$offItem();
    @Accessor("offHandItem") void tasp$setOffItem(ItemStack s);
    @Accessor("mainHandHeight") float tasp$main();
    @Accessor("mainHandHeight") void tasp$setMain(float f);
    @Accessor("oMainHandHeight") float tasp$mainO();
    @Accessor("oMainHandHeight") void tasp$setMainO(float f);
    @Accessor("offHandHeight") float tasp$off();
    @Accessor("offHandHeight") void tasp$setOff(float f);
    @Accessor("oOffHandHeight") float tasp$offO();
    @Accessor("oOffHandHeight") void tasp$setOffO(float f);
}
