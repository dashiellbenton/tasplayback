package dev.dbenton.tasplayback.legacy.forge;

import dev.dbenton.tasplayback.core.TASPlaybackCore;
import dev.dbenton.tasplayback.legacy.TASPlaybackLegacy;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

@Mod(modid = TASPlaybackCore.MOD_ID, name = "TASPlayback", clientSideOnly = true,
        acceptedMinecraftVersions = "[1.12.2]", dependencies = "required-after:mixinbooter")
public final class TASPlaybackForge {
    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        String version = Loader.instance().activeModContainer().getVersion();
        TASPlaybackLegacy.onClientInit("forge", version);
    }
}
