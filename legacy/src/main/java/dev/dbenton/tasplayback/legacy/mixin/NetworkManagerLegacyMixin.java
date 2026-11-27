package dev.dbenton.tasplayback.legacy.mixin;

import dev.dbenton.tasplayback.legacy.LegacyClient;
import net.minecraft.network.NetworkManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.SocketAddress;

/** Recording: each singleplayer connection starts a segment. */
@Mixin(NetworkManager.class)
abstract class NetworkManagerLegacyMixin {
    @Inject(method = "provideLocalClient", at = @At("RETURN"))
    private static void tasp$localClient(SocketAddress address, CallbackInfoReturnable<NetworkManager> cir) {
        LegacyClient.get().onLocalConnection();
    }
}
