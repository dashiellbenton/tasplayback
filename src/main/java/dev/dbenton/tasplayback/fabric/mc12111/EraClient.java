package dev.dbenton.tasplayback.fabric.mc12111;

/** Entry point of the 1.21.11 implementation, looked up reflectively by the common bootstrap. */
public final class EraClient {
    private EraClient() {}

    public static void init() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> TaspClient.get().onShutdown(), "TASPlayback-Shutdown"));
    }
}
