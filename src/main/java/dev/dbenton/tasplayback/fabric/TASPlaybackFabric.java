package dev.dbenton.tasplayback.fabric;

import dev.dbenton.tasplayback.core.TASPlaybackCore;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class TASPlaybackFabric implements ClientModInitializer {
    public static final Logger LOGGER = LogManager.getLogger("TASPlayback");

    @Override
    public void onInitializeClient() {
        String mc = FabricLoader.getInstance().getModContainer("minecraft")
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
        String self = FabricLoader.getInstance().getModContainer(TASPlaybackCore.MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
        LOGGER.info(TASPlaybackCore.describe("fabric", mc, self));
    }
}
