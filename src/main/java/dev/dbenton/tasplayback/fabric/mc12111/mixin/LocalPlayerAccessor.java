package dev.dbenton.tasplayback.fabric.mc12111.mixin;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LocalPlayer.class)
public interface LocalPlayerAccessor {
    @Accessor("startedUsingItem") boolean tasp$startedUsingItem();
    @Accessor("startedUsingItem") void tasp$setStartedUsingItem(boolean b);
    @Accessor("usingItemHand") void tasp$setUsingItemHand(net.minecraft.world.InteractionHand h);
}
