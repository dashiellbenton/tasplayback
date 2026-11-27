package dev.dbenton.tasplayback.fabric.mc12111.mixin;

import dev.dbenton.tasplayback.fabric.mc12111.TaspClient;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Recording: block changes the client predicts for its own actions (breaking, placing) before the server
 * confirms them. Packets alone would show them a few ticks late. Read-only observation of the result.
 */
@Mixin(ClientLevel.class)
abstract class ClientLevelEraMixin {
    @Shadow @Final private BlockStatePredictionHandler blockStatePredictionHandler;

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("RETURN"))
    private void tasp$predicted(BlockPos pos, BlockState state, int flags, int depth, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && blockStatePredictionHandler.isPredicting()) {
            TaspClient.get().onPredictedBlock(pos, state, flags, depth);
        }
    }
}
