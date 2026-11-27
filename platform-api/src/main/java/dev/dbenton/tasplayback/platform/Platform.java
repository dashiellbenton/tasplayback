package dev.dbenton.tasplayback.platform;

/**
 * Implemented once per loader/version node. Grows in later milestones into the
 * CameraSource / GuiStateSource / SoundTap / PacketTap / ... family described in PLAN.md.
 */
public interface Platform {
    /** "fabric", "forge" or "legacyfabric". */
    String loader();

    /** The running Minecraft version string. */
    String minecraftVersion();
}
