package dev.dbenton.tasplayback.legacy;

import dev.dbenton.tasplayback.core.TASPlaybackCore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Shared 1.12.2 implementation (MCP names). Used by both the Forge and the LegacyFabric node;
 * loader-specific glue lives in legacy/src/forge and legacy/src/legacyfabric.
 */
public final class TASPlaybackLegacy {
    public static final Logger LOGGER = LogManager.getLogger("TASPlayback");
    public static final String MC_VERSION = "1.12.2";

    private TASPlaybackLegacy() {}

    public static void onClientInit(String loader, String modVersion) {
        LOGGER.info(TASPlaybackCore.describe(loader, MC_VERSION, modVersion));
    }
}
