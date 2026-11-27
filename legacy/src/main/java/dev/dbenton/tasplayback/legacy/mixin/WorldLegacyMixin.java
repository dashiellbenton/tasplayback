package dev.dbenton.tasplayback.legacy.mixin;

import dev.dbenton.tasplayback.legacy.LegacyClient;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Recording: client-side block changes made by the player's own actions (see PlayerControllerMPLegacyMixin). */
@Mixin(World.class)
abstract class WorldLegacyMixin {
    @Shadow @Final public boolean isRemote;

    @Inject(method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;I)Z", at = @At("RETURN"))
    private void tasp$clientChange(BlockPos pos, IBlockState state, int flags, CallbackInfoReturnable<Boolean> cir) {
        if (isRemote && cir.getReturnValueZ()) LegacyClient.get().onClientBlockChange(pos, state, flags);
    }
}
