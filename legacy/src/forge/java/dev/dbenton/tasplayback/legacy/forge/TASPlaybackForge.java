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
        final String version = Loader.instance().activeModContainer().getVersion();
        TASPlaybackLegacy.onClientInit(new dev.dbenton.tasplayback.legacy.LoaderGlue() {
            @Override public String loader() { return "forge"; }
            @Override public String modVersion() { return version; }
            @Override public java.nio.file.Path gameDir() { return net.minecraft.client.Minecraft.getMinecraft().gameDir.toPath(); }
            @Override public void prepareReplayChannel(io.netty.channel.Channel channel, net.minecraft.network.NetworkManager manager) {
                // Forge's handleJoinGame asks the connection's NetworkDispatcher for the dimension override.
                channel.attr(net.minecraftforge.fml.common.network.handshake.NetworkDispatcher.FML_DISPATCHER)
                        .set(new net.minecraftforge.fml.common.network.handshake.NetworkDispatcher(manager));
            }
        });
    }
}
