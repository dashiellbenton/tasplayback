package dev.dbenton.tasplayback.fabric.mc12111.mixin;

import dev.dbenton.tasplayback.fabric.mc12111.TaspClient;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.SocketAddress;

/** Recording: every singleplayer connection gets a pipeline tee (read-only copy of inbound frames). */
@Mixin(Connection.class)
abstract class ConnectionMixin {
    @Inject(method = "connectToLocalServer", at = @At("RETURN"))
    private static void tasp$afterLocalConnect(SocketAddress address, CallbackInfoReturnable<Connection> cir) {
        Connection c = cir.getReturnValue();
        TaspClient.get().onLocalConnection(c, ((ConnectionAccessor) c).tasp$channel());
    }
}
