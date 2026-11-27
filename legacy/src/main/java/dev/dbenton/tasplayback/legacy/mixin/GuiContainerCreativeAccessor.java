package dev.dbenton.tasplayback.legacy.mixin;

import net.minecraft.client.gui.inventory.GuiContainerCreative;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GuiContainerCreative.class)
public interface GuiContainerCreativeAccessor {
    @Accessor("currentScroll") float tasp$scroll();
    @Accessor("currentScroll") void tasp$setScroll(float f);
}
