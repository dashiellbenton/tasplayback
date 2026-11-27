package dev.dbenton.tasplayback.legacy.legacyfabric;

import dev.dbenton.tasplayback.core.TASPlaybackCore;
import dev.dbenton.tasplayback.legacy.TASPlaybackLegacy;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public final class TASPlaybackLegacyFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        final String version = FabricLoader.getInstance().getModContainer(TASPlaybackCore.MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
        TASPlaybackLegacy.onClientInit(new dev.dbenton.tasplayback.legacy.LoaderGlue() {
            @Override public String loader() { return "legacyfabric"; }
            @Override public String modVersion() { return version; }
            @Override public java.nio.file.Path gameDir() { return FabricLoader.getInstance().getGameDir(); }
            @Override public void prepareReplayChannel(io.netty.channel.Channel channel, net.minecraft.network.NetworkManager manager) { }
        });
    }
}
