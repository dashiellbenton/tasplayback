package dev.dbenton.tasplayback.fabric.mc12111;

import dev.dbenton.tasplayback.core.event.EventRecord;
import dev.dbenton.tasplayback.core.io.ByteSource;
import dev.dbenton.tasplayback.core.replay.GuiEvents;
import dev.dbenton.tasplayback.fabric.TASPlaybackFabric;
import dev.dbenton.tasplayback.fabric.mc12111.mixin.KeyboardHandlerInvoker;
import dev.dbenton.tasplayback.fabric.mc12111.mixin.MouseHandlerAccessor;
import dev.dbenton.tasplayback.fabric.mc12111.mixin.MouseHandlerInvoker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;

import java.util.HashSet;
import java.util.Set;

/** Applies GUI-stream events during playback. */
final class GuiReplay {
    private final Set<String> warned = new HashSet<>();

    void apply(Minecraft mc, EventRecord e, MenuState menus) {
        switch (e.type) {
            case GuiEvents.INPUT: dispatch(mc, e.payload); break;
            case GuiEvents.SCREEN: screen(mc, e.payload); break;
            case GuiEvents.MENU_STATE: menus.apply(mc, e.payload); break;
            case GuiEvents.DEBUG_TEXT: DebugText.apply(e.payload); break;
            default: break;
        }
    }

    private void dispatch(Minecraft mc, byte[] payload) {
        ByteSource in = new ByteSource(payload);
        int kind = in.readUnsignedByte();
        double x = in.readDouble(), y = in.readDouble();
        long millis = in.readLong();
        long window = mc.getWindow().handle();
        MouseHandlerAccessor m = (MouseHandlerAccessor) mc.mouseHandler;
        GuiRecorder.dispatching = true;
        GuiRecorder.dispatchMillis = millis;
        try {
            if (kind != GuiRecorder.MOVE) { m.tasp$setX(x); m.tasp$setY(y); }
            switch (kind) {
                case GuiRecorder.BUTTON: {
                    int button = in.readVarInt(), mods = in.readVarInt(), action = in.readVarInt();
                    ((MouseHandlerInvoker) mc.mouseHandler).tasp$onButton(window, new MouseButtonInfo(button, mods), action);
                    break;
                }
                case GuiRecorder.MOVE:
                    ((MouseHandlerInvoker) mc.mouseHandler).tasp$onMove(window, in.readDouble(), in.readDouble());
                    break;
                case GuiRecorder.SCROLL:
                    ((MouseHandlerInvoker) mc.mouseHandler).tasp$onScroll(window, in.readDouble(), in.readDouble());
                    break;
                case GuiRecorder.KEY: {
                    int key = in.readVarInt(), scan = in.readVarInt(), mods = in.readVarInt(), action = in.readVarInt();
                    ((KeyboardHandlerInvoker) mc.keyboardHandler).tasp$keyPress(window, action, new KeyEvent(key, scan, mods));
                    break;
                }
                case GuiRecorder.CHAR: {
                    int cp = in.readVarInt(), mods = in.readVarInt();
                    ((KeyboardHandlerInvoker) mc.keyboardHandler).tasp$charTyped(window, new CharacterEvent(cp, mods));
                    break;
                }
                default:
                    break;
            }
        } finally {
            GuiRecorder.dispatching = false;
        }
    }

    private static String readClassName(byte[] payload) {
        ByteSource in = new ByteSource(payload);
        in.readUnsignedByte();
        return in.readString();
    }

    /** Opens the screens the client opens by itself; server-opened screens arrive through packets. */
    private void screen(Minecraft mc, byte[] payload) {
        ByteSource in = new ByteSource(payload);
        int kind = in.readUnsignedByte();
        Screen cur = mc.screen;
        switch (kind) {
            case GuiRecorder.SCREEN_NONE:
                if (cur instanceof AbstractContainerScreen) cur.onClose();
                else if (cur != null) mc.setScreen(null);
                break;
            case GuiRecorder.SCREEN_INVENTORY:
                if (!(cur instanceof InventoryScreen) && mc.player != null) mc.setScreen(new InventoryScreen(mc.player));
                break;
            case GuiRecorder.SCREEN_CREATIVE:
                if (!(cur instanceof CreativeModeInventoryScreen) && mc.player != null) {
                    mc.setScreen(new CreativeModeInventoryScreen(mc.player, mc.player.connection.enabledFeatures(), mc.options.operatorItemsTab().get()));
                }
                break;
            case GuiRecorder.SCREEN_CHAT: {
                String initial = in.readString();
                boolean draft = in.readBoolean();
                if (!(cur instanceof ChatScreen)) mc.setScreen(new ChatScreen(initial, draft));
                break;
            }
            case GuiRecorder.SCREEN_PAUSE: {
                boolean menu = in.readBoolean();
                if (!(cur instanceof PauseScreen)) mc.setScreen(new PauseScreen(menu));
                break;
            }
            default: {
                in.readString();
                break;
            }
        }
        if (kind == GuiRecorder.SCREEN_OTHER) {
            String cls = readClassName(payload);
            if (cls.length() > 0 && (mc.screen == null || !mc.screen.getClass().getName().equals(cls)) && warned.add(cls)) {
                TASPlaybackFabric.LOGGER.info("Replay: recorded screen {} is opened by the server or not reconstructible yet", cls);
            }
        }
    }
}
