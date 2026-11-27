package dev.dbenton.tasplayback.fabric.mc12111.mixin;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MouseHandler.class)
public interface MouseHandlerInvoker {
    @Invoker("onButton") void tasp$onButton(long window, MouseButtonInfo info, int action);
    @Invoker("onScroll") void tasp$onScroll(long window, double dx, double dy);
    @Invoker("onMove") void tasp$onMove(long window, double x, double y);
}
