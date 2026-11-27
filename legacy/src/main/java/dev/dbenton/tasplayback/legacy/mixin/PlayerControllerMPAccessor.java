package dev.dbenton.tasplayback.legacy.mixin;

import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerControllerMP.class)
public interface PlayerControllerMPAccessor {
    @Accessor("currentBlock") BlockPos tasp$currentBlock();
    @Accessor("curBlockDamageMP") float tasp$damage();
}
