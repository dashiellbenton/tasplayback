package dev.dbenton.tasplayback.legacy.mixin;

import dev.dbenton.tasplayback.legacy.LegacyClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.INetHandler;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketThreadUtil;
import net.minecraft.util.IThreadListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Recording: a packet is being handled on the client main thread right now. Read-only. */
@Mixin(PacketThreadUtil.class)
abstract class PacketThreadUtilLegacyMixin {
    @Inject(method = "checkThreadAndEnqueue", at = @At("HEAD"))
    private static void tasp$handled(Packet<?> packet, INetHandler handler, IThreadListener scheduler, CallbackInfo ci) {
        if (scheduler instanceof Minecraft && scheduler.isCallingFromMinecraftThread()) LegacyClient.get().onMainThreadPacket(packet);
    }
}
