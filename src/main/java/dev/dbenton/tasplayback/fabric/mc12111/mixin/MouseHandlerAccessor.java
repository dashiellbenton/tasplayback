package dev.dbenton.tasplayback.fabric.mc12111.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MouseHandler.class)
public interface MouseHandlerAccessor {
    @Accessor("xpos") double tasp$getX();
    @Accessor("xpos") void tasp$setX(double x);
    @Accessor("ypos") double tasp$getY();
    @Accessor("ypos") void tasp$setY(double y);
    @Accessor("isLeftPressed") boolean tasp$left();
    @Accessor("isMiddlePressed") boolean tasp$middle();
    @Accessor("isRightPressed") boolean tasp$right();
}
