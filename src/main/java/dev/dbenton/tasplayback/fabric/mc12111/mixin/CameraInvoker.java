package dev.dbenton.tasplayback.fabric.mc12111.mixin;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Camera.class)
public interface CameraInvoker {
    @Invoker("setPosition") void tasp$setPosition(double x, double y, double z);
    @Invoker("setRotation") void tasp$setRotation(float yaw, float pitch);
}
