package dev.dbenton.tasplayback.core;

/**
 * Version-neutral entry point shared by every loader/version node.
 * Contains no Minecraft imports; everything in {@code core} must stay that way.
 */
public final class TASPlaybackCore {
    public static final String MOD_ID = "tasplayback";
    /** Bumped whenever any .tasp stream layout changes. */
    public static final int FORMAT_VERSION = 1;

    private TASPlaybackCore() {}

    public static String describe(String loader, String mcVersion, String modVersion) {
        return "TASPlayback " + modVersion + " loaded (loader=" + loader + ", minecraft=" + mcVersion
                + ", format=" + FORMAT_VERSION + ")";
    }
}
