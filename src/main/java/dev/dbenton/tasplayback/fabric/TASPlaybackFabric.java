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
        // Version-specific implementation, if this node has one (see build.gradle.kts "era").
        for (String era : new String[] { "mc12111" }) {
            try {
                Class.forName("dev.dbenton.tasplayback.fabric." + era + ".EraClient").getMethod("init").invoke(null);
                LOGGER.info("TASPlayback recording/playback enabled ({})", era);
                return;
            } catch (ClassNotFoundException notThisNode) {
                // not compiled into this node
            } catch (ReflectiveOperationException e) {
                LOGGER.error("Failed to start TASPlayback ({})", era, e);
                return;
            }
        }
        LOGGER.warn("TASPlayback has no recording/playback implementation for this Minecraft version yet");
    }

    public static String modVersion() {
        return FabricLoader.getInstance().getModContainer(TASPlaybackCore.MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
    }
}
