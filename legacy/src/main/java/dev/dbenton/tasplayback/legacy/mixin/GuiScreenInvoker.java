package dev.dbenton.tasplayback.legacy.mixin;

import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GuiScreen.class)
public interface GuiScreenInvoker {
    @Invoker("mouseClicked") void tasp$mouseClicked(int x, int y, int button);
    @Invoker("mouseReleased") void tasp$mouseReleased(int x, int y, int state);
    @Invoker("mouseClickMove") void tasp$mouseClickMove(int x, int y, int button, long time);
    @Invoker("keyTyped") void tasp$keyTyped(char c, int key);
}
