package dev.dbenton.tasplayback.fabric.mc12111;

import dev.dbenton.tasplayback.core.io.ByteSink;
import dev.dbenton.tasplayback.core.replay.GuiEvents;
import dev.dbenton.tasplayback.fabric.mc12111.mixin.ChatScreenAccessor;
import dev.dbenton.tasplayback.fabric.mc12111.mixin.MouseHandlerAccessor;
import dev.dbenton.tasplayback.fabric.mc12111.mixin.PauseScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

/**
 * GUI input recording (entry points called from the input mixins) and the playback-side switches.
 * Recording reads the event and the cursor position only; it never alters or consumes input.
 */
public final class GuiRecorder {
    static final int BUTTON = 1, MOVE = 2, SCROLL = 3, KEY = 4, CHAR = 5;
    static final int SCREEN_NONE = 0, SCREEN_INVENTORY = 1, SCREEN_CREATIVE = 2, SCREEN_CHAT = 3, SCREEN_PAUSE = 4, SCREEN_OTHER = 9;

    /** True while playback dispatches a recorded event (main thread only). */
    static boolean dispatching;
    /** Recorded wall-clock time of the event being dispatched. */
    static long dispatchMillis;

    private GuiRecorder() {}

    public static boolean blockLiveInput() {
        return TaspClient.get().isReplaying() && !dispatching;
    }

    public static long eventMillis(long vanilla) {
        return dispatching ? dispatchMillis : vanilla;
    }

    private static ByteSink header(int kind) {
        Minecraft mc = Minecraft.getInstance();
        MouseHandlerAccessor m = (MouseHandlerAccessor) mc.mouseHandler;
        ByteSink s = new ByteSink(48);
        s.writeByte(kind).writeDouble(m.tasp$getX()).writeDouble(m.tasp$getY()).writeLong(System.nanoTime() / 1_000_000L);
        return s;
    }

    private static boolean recordingScreenInput() {
        TaspClient c = TaspClient.get();
        return c.isRecording() && !c.isReplaying() && Minecraft.getInstance().screen != null;
    }

    private static void emit(int type, ByteSink s) {
        Tripwire.enter();
        try {
            TaspClient.get().onGuiEvent(type, s.toByteArray());
        } finally {
            Tripwire.exit();
        }
    }

    public static void onButton(int button, int modifiers, int action) {
        if (!recordingScreenInput()) return;
        emit(GuiEvents.INPUT, header(BUTTON).writeVarInt(button).writeVarInt(modifiers).writeVarInt(action));
    }

    public static void onMove(double x, double y) {
        if (!recordingScreenInput()) return;
        emit(GuiEvents.INPUT, header(MOVE).writeDouble(x).writeDouble(y));
    }

    public static void onScroll(double dx, double dy) {
        if (!recordingScreenInput()) return;
        emit(GuiEvents.INPUT, header(SCROLL).writeDouble(dx).writeDouble(dy));
    }

    public static void onKey(int key, int scancode, int modifiers, int action) {
        if (!recordingScreenInput()) return;
        emit(GuiEvents.INPUT, header(KEY).writeVarInt(key).writeVarInt(scancode).writeVarInt(modifiers).writeVarInt(action));
    }

    public static void onChar(int codepoint, int modifiers) {
        if (!recordingScreenInput()) return;
        emit(GuiEvents.INPUT, header(CHAR).writeVarInt(codepoint).writeVarInt(modifiers));
    }

    public static void onSetScreen(Screen screen) {
        TaspClient c = TaspClient.get();
        if (!c.isRecording() || c.isReplaying() || Minecraft.getInstance().level == null) return;
        ByteSink s = new ByteSink(64);
        if (screen == null) {
            s.writeByte(SCREEN_NONE);
        } else if (screen instanceof CreativeModeInventoryScreen) {
            s.writeByte(SCREEN_CREATIVE);
        } else if (screen instanceof InventoryScreen) {
            s.writeByte(SCREEN_INVENTORY);
        } else if (screen instanceof ChatScreen) {
            ChatScreenAccessor a = (ChatScreenAccessor) screen;
            s.writeByte(SCREEN_CHAT).writeString(a.tasp$initial() == null ? "" : a.tasp$initial()).writeBoolean(a.tasp$isDraft());
        } else if (screen instanceof PauseScreen) {
            s.writeByte(SCREEN_PAUSE).writeBoolean(((PauseScreenAccessor) screen).tasp$showPauseMenu());
        } else {
            s.writeByte(SCREEN_OTHER);
        }
        s.writeString(screen == null ? "" : screen.getClass().getName());
        emit(GuiEvents.SCREEN, s);
    }
}
