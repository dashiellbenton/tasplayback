package dev.dbenton.tasplayback.fabric.mc12111.mixin;

import dev.dbenton.tasplayback.fabric.mc12111.TaspClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Recording: notes the moment the client main thread handles a queued packet. Read-only. */
@Mixin(targets = "net.minecraft.network.PacketProcessor$ListenerAndPacket")
abstract class PacketProcessorMixin {
    @Shadow @Final private Packet<?> packet;

    @Inject(method = "handle", at = @At("HEAD"))
    private void tasp$onHandle(CallbackInfo ci) {
        if (Minecraft.getInstance().isSameThread()) TaspClient.get().onMainThreadPacket(packet);
    }
}
