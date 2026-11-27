package dev.dbenton.tasplayback.fabric.mc12111.mixin;

import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChatScreen.class)
public interface ChatScreenAccessor {
    @Accessor("initial") String tasp$initial();
    @Accessor("isDraft") boolean tasp$isDraft();
}
