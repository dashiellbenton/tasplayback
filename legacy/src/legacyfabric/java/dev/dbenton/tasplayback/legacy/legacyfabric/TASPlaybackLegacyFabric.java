package dev.dbenton.tasplayback.legacy.legacyfabric;

import dev.dbenton.tasplayback.core.TASPlaybackCore;
import dev.dbenton.tasplayback.legacy.TASPlaybackLegacy;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public final class TASPlaybackLegacyFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        String version = FabricLoader.getInstance().getModContainer(TASPlaybackCore.MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
        TASPlaybackLegacy.onClientInit("legacyfabric", version);
    }
}
