package dev.dbenton.tasplayback.legacy;

import io.netty.channel.Channel;
import net.minecraft.network.NetworkManager;

/** The few things that differ between Forge and LegacyFabric on 1.12.2. Set by the loader entrypoint. */
public interface LoaderGlue {
    String loader();

    String modVersion();

    /** The game directory (.minecraft). */
    java.nio.file.Path gameDir();

    /** Prepares a replay channel before the play handler is used (Forge needs its NetworkDispatcher attached). */
    void prepareReplayChannel(Channel channel, NetworkManager manager);
}
