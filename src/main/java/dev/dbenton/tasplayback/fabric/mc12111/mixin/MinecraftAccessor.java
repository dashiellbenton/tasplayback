package dev.dbenton.tasplayback.fabric.mc12111.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Accessor("pendingConnection") Connection tasp$getPendingConnection();
    @Accessor("pendingConnection") void tasp$setPendingConnection(Connection c);
    @Accessor("deltaTracker") DeltaTracker.Timer tasp$getTimer();
    @Accessor("pause") boolean tasp$isPauseFlag();
}
