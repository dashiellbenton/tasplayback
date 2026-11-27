package dev.dbenton.tasplayback.core.replay;

/**
 * Event types of the GUI stream (payloads version-specific).
 *
 * <ul>
 *   <li>{@link #INPUT}: a raw mouse/keyboard event that reached the client while a screen was open
 *       (button, move, scroll, key, character), with the cursor position and wall-clock time at that moment.
 *       Playback dispatches it into the same vanilla handler, so the screen's own code rebuilds its
 *       client-side state (recipe book, search fields, tabs, scroll, trades, typed text, ...).</li>
 *   <li>{@link #SCREEN}: the client switched screens ({@code setScreen}); playback opens the screens the
 *       client opens by itself (inventory, chat, pause, creative). Server-opened screens come from packets.</li>
 *   <li>{@link #MENU_STATE}: changed slots and cursor stack of the open menu, captured after each frame.
 *       A safety net restoring exactly what the recording showed.</li>
 * </ul>
 */
public final class GuiEvents {
    private GuiEvents() {}

    public static final int INPUT = 1;
    public static final int SCREEN = 2;
    public static final int MENU_STATE = 3;
    /** Debug overlay (F3) text lines as shown, when they changed; replaces the viewer's live values. */
    public static final int DEBUG_TEXT = 4;
    /** Screen state with no input event behind it (e.g. the creative inventory's scroll position); version-specific. */
    public static final int SCREEN_STATE = 5;
}
