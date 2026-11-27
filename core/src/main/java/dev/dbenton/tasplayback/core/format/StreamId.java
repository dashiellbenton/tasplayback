package dev.dbenton.tasplayback.core.format;

/** The binary streams of a recording. Each has its own file and its own format version. */
public enum StreamId {
    /** Per-render-frame camera / first-person / mouse state. */
    FRAMES(1, "frames.bin", 1),
    /** GUI state events plus periodic full keyframes. */
    GUI(2, "gui.bin", 1),
    /** HUD and inventory state changes, per tick. */
    HUD(3, "hud.bin", 1),
    /** Sound play / stop / update events. */
    SOUNDS(4, "sounds.bin", 1),
    /** Clientbound packets plus injected local-player state. */
    WORLD(5, "world.bin", 1),
    /** Optional captured GUI layer (fallback for unknown screens). Lowest priority; dropped first under load. */
    GUICAP(6, "guicap.bin", 1);

    public final int id;
    public final String fileName;
    /** Current format version written by this build. */
    public final int version;

    StreamId(int id, String fileName, int version) {
        this.id = id;
        this.fileName = fileName;
        this.version = version;
    }

    public static StreamId byId(int id) {
        for (StreamId s : values()) if (s.id == id) return s;
        throw new IllegalArgumentException("unknown stream id " + id);
    }
}
