package dev.dbenton.tasplayback.legacy;

import dev.dbenton.tasplayback.core.event.EventRecord;
import dev.dbenton.tasplayback.core.io.ByteSink;
import dev.dbenton.tasplayback.core.io.ByteSource;
import dev.dbenton.tasplayback.core.replay.GuiEvents;
import dev.dbenton.tasplayback.legacy.mixin.GuiChatAccessor;
import dev.dbenton.tasplayback.legacy.mixin.GuiContainerCreativeAccessor;
import dev.dbenton.tasplayback.legacy.mixin.GuiScreenAccessor;
import dev.dbenton.tasplayback.legacy.mixin.GuiScreenInvoker;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.NonNullList;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 1.12.2 GUI recording and playback (see GuiEvents). Input is recorded as the resolved arguments vanilla
 * passes to the screen (mouseClicked/mouseReleased/mouseClickMove/keyTyped) and replayed by calling the same
 * methods; client-opened screens are reopened; menu slots, the cursor stack, the creative scroll position
 * and the F3 text are restored as state.
 */
public final class LegacyGui {
    public static final int CLICK = 1, RELEASE = 2, DRAG = 3, KEY = 4;
    static final int SCREEN_NONE = 0, SCREEN_INVENTORY = 1, SCREEN_CREATIVE = 2, SCREEN_CHAT = 3, SCREEN_PAUSE = 4, SCREEN_OTHER = 9;
    static final int STATE_CREATIVE_SCROLL = 1;

    public static boolean dispatching;
    public static boolean shift, ctrl, alt;

    private LegacyGui() {}

    // ------------------------------------------------------------------ recording

    private static boolean recordingScreen() {
        LegacyClient c = LegacyClient.get();
        return c.isRecording() && !c.isReplaying() && Minecraft.getMinecraft().currentScreen != null;
    }

    private static ByteSink mods(ByteSink s) {
        return s.writeBoolean(GuiScreen.isShiftKeyDown()).writeBoolean(GuiScreen.isCtrlKeyDown()).writeBoolean(GuiScreen.isAltKeyDown());
    }

    public static void onMouse(int kind, int x, int y, int button, long time) {
        if (!recordingScreen() || dispatching) return;
        ByteSink s = new ByteSink(32).writeByte(kind).writeZigZagInt(x).writeZigZagInt(y).writeZigZagInt(button).writeVarLong(Math.max(0, time));
        LegacyClient.get().onGuiEvent(GuiEvents.INPUT, mods(s).toByteArray());
    }

    public static void onKey(char c, int key) {
        if (!recordingScreen() || dispatching) return;
        ByteSink s = new ByteSink(16).writeByte(KEY).writeVarInt(c).writeVarInt(key);
        LegacyClient.get().onGuiEvent(GuiEvents.INPUT, mods(s).toByteArray());
    }

    public static void onDisplayScreen(GuiScreen screen) {
        LegacyClient c = LegacyClient.get();
        Minecraft mc = Minecraft.getMinecraft();
        if (!c.isRecording() || c.isReplaying() || mc.world == null) return;
        ByteSink s = new ByteSink(64);
        if (screen == null) s.writeByte(SCREEN_NONE);
        else if (screen instanceof GuiContainerCreative) s.writeByte(SCREEN_CREATIVE);
        else if (screen instanceof GuiInventory) s.writeByte(SCREEN_INVENTORY);
        else if (screen instanceof GuiChat) s.writeByte(SCREEN_CHAT).writeString(((GuiChatAccessor) screen).tasp$defaultText());
        else if (screen instanceof GuiIngameMenu) s.writeByte(SCREEN_PAUSE);
        else s.writeByte(SCREEN_OTHER);
        s.writeString(screen == null ? "" : screen.getClass().getName());
        if (screen instanceof GuiIngameMenu) {
            // what GuiIngameMenu.initGui derives from the integrated server, which a replay does not have
            s.writeBoolean(mc.isIntegratedServerRunning());
            s.writeBoolean(mc.isSingleplayer() && mc.getIntegratedServer() != null && !mc.getIntegratedServer().getPublic());
        }
        c.onGuiEvent(GuiEvents.SCREEN, s.toByteArray());
    }

    private static final List<List<String>> lastDebug = new ArrayList<>();
    private static final List<List<String>> replayDebug = new ArrayList<>();
    static {
        for (int i = 0; i < 2; i++) { lastDebug.add(new ArrayList<String>()); replayDebug.add(new ArrayList<String>()); }
    }

    static void resetDebug() { for (List<String> l : lastDebug) l.clear(); for (List<String> l : replayDebug) l.clear(); }

    public static List<String> debugLines(List<String> lines, boolean left) {
        LegacyClient c = LegacyClient.get();
        int side = left ? 0 : 1;
        if (c.isReplaying()) return new ArrayList<>(replayDebug.get(side));
        if (c.isRecording() && lines != null && !lines.equals(lastDebug.get(side))) {
            lastDebug.get(side).clear();
            lastDebug.get(side).addAll(lines);
            ByteSink s = new ByteSink(256).writeBoolean(left).writeVarInt(lines.size());
            for (String l : lines) s.writeString(l == null ? "" : l);
            c.onGuiEvent(GuiEvents.DEBUG_TEXT, s.toByteArray());
        }
        return lines;
    }

    /** Per-frame state capture (menu slots, cursor stack, creative scroll). Read-only. */
    static final class FrameState {
        private int lastWindow = Integer.MIN_VALUE;
        private ItemStack[] last = new ItemStack[0];
        private ItemStack lastCarried = ItemStack.EMPTY;
        private float lastScroll = Float.NaN;

        void reset() { lastWindow = Integer.MIN_VALUE; last = new ItemStack[0]; lastCarried = ItemStack.EMPTY; lastScroll = Float.NaN; }

        byte[] menu(Container c, ItemStack carried) {
            NonNullList<ItemStack> items = c.getInventory();
            boolean full = c.windowId != lastWindow || last.length != items.size();
            List<Integer> changed = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) if (full || !ItemStack.areItemStacksEqual(items.get(i), last[i])) changed.add(i);
            boolean carriedChanged = full || !ItemStack.areItemStacksEqual(carried, lastCarried);
            if (changed.isEmpty() && !carriedChanged) return null;
            PacketBuffer b = new PacketBuffer(Unpooled.buffer(64));
            try {
                b.writeVarInt(c.windowId);
                b.writeVarInt(changed.size());
                if (full) last = new ItemStack[items.size()];
                for (int i : changed) {
                    b.writeVarInt(i);
                    b.writeItemStack(items.get(i));
                    last[i] = items.get(i).copy();
                }
                b.writeBoolean(carriedChanged);
                if (carriedChanged) { b.writeItemStack(carried); lastCarried = carried.copy(); }
                lastWindow = c.windowId;
                byte[] out = new byte[b.readableBytes()];
                b.readBytes(out);
                return out;
            } finally {
                b.release();
            }
        }

        byte[] creativeScroll(GuiScreen screen) {
            if (!(screen instanceof GuiContainerCreative)) { lastScroll = Float.NaN; return null; }
            float v = ((GuiContainerCreativeAccessor) screen).tasp$scroll();
            if (Float.floatToIntBits(v) == Float.floatToIntBits(lastScroll)) return null;
            lastScroll = v;
            return new ByteSink(8).writeByte(STATE_CREATIVE_SCROLL).writeFloat(v).toByteArray();
        }
    }

    /** Dev test only: what the wrapped call sites in GuiScreen.handle*Input do (LWJGL 2 cannot inject input events). */
    static void simulateMouse(GuiScreen s, int kind, int x, int y, int button) {
        long time = Minecraft.getSystemTime();
        onMouse(kind, x, y, button, time);
        GuiScreenInvoker inv = (GuiScreenInvoker) s;
        if (kind == CLICK) inv.tasp$mouseClicked(x, y, button);
        else if (kind == RELEASE) inv.tasp$mouseReleased(x, y, button);
        else inv.tasp$mouseClickMove(x, y, button, time);
    }

    static void simulateKey(GuiScreen s, char c, int key) {
        onKey(c, key);
        ((GuiScreenInvoker) s).tasp$keyTyped(c, key);
    }

    // ------------------------------------------------------------------ playback

    private static boolean pauseIntegrated = true, pauseLan = true;

    public static void adjustPauseMenu(GuiScreen menu) {
        if (!LegacyClient.get().isReplaying()) return;
        for (net.minecraft.client.gui.GuiButton b : ((GuiScreenAccessor) menu).tasp$buttons()) {
            if (b.id == 1) b.displayString = net.minecraft.client.resources.I18n.format(pauseIntegrated ? "menu.returnToMenu" : "menu.disconnect");
            if (b.id == 7) b.enabled = pauseLan;
        }
    }

    /** Called from GuiScreen.handleInput: during replay, dispatch due recorded events instead of the live queue. */
    public static boolean replayInput(GuiScreen screen) {
        LegacyClient c = LegacyClient.get();
        if (!c.isReplaying()) return false;
        c.replay().dispatchDueGui();
        return true;
    }

    static void apply(Minecraft mc, EventRecord e) throws IOException {
        switch (e.type) {
            case GuiEvents.INPUT: dispatch(mc, e.payload); break;
            case GuiEvents.SCREEN: screen(mc, e.payload); break;
            case GuiEvents.MENU_STATE: menu(mc, e.payload); break;
            case GuiEvents.DEBUG_TEXT: {
                ByteSource in = new ByteSource(e.payload);
                List<String> l = replayDebug.get(in.readBoolean() ? 0 : 1);
                l.clear();
                int n = in.readVarInt();
                for (int i = 0; i < n; i++) l.add(in.readString());
                break;
            }
            case GuiEvents.SCREEN_STATE: {
                ByteSource in = new ByteSource(e.payload);
                if (in.readUnsignedByte() == STATE_CREATIVE_SCROLL && mc.currentScreen instanceof GuiContainerCreative) {
                    float v = in.readFloat();
                    GuiContainerCreative g = (GuiContainerCreative) mc.currentScreen;
                    ((GuiContainerCreativeAccessor) g).tasp$setScroll(v);
                    ((GuiContainerCreative.ContainerCreative) g.inventorySlots).scrollTo(v);
                }
                break;
            }
            default: break;
        }
    }

    private static void dispatch(Minecraft mc, byte[] payload) throws IOException {
        GuiScreen s = mc.currentScreen;
        if (s == null) return;
        ByteSource in = new ByteSource(payload);
        int kind = in.readUnsignedByte();
        GuiScreenInvoker inv = (GuiScreenInvoker) s;
        dispatching = true;
        try {
            if (kind == KEY) {
                char c = (char) in.readVarInt();
                int key = in.readVarInt();
                shift = in.readBoolean(); ctrl = in.readBoolean(); alt = in.readBoolean();
                inv.tasp$keyTyped(c, key);
            } else {
                int x = in.readZigZagInt(), y = in.readZigZagInt(), b = in.readZigZagInt();
                long time = in.readVarLong();
                shift = in.readBoolean(); ctrl = in.readBoolean(); alt = in.readBoolean();
                if (kind == CLICK) inv.tasp$mouseClicked(x, y, b);
                else if (kind == RELEASE) inv.tasp$mouseReleased(x, y, b);
                else if (kind == DRAG) inv.tasp$mouseClickMove(x, y, b, time);
            }
        } finally {
            dispatching = false;
        }
    }

    private static void screen(Minecraft mc, byte[] payload) {
        ByteSource in = new ByteSource(payload);
        int kind = in.readUnsignedByte();
        GuiScreen cur = mc.currentScreen;
        switch (kind) {
            case SCREEN_NONE:
                if (cur instanceof GuiContainer && mc.player != null) mc.player.closeScreen();
                else if (cur != null) mc.displayGuiScreen(null);
                break;
            case SCREEN_INVENTORY:
                if (!(cur instanceof GuiInventory) && mc.player != null) mc.displayGuiScreen(new GuiInventory(mc.player));
                break;
            case SCREEN_CREATIVE:
                if (!(cur instanceof GuiContainerCreative) && mc.player != null) mc.displayGuiScreen(new GuiContainerCreative(mc.player));
                break;
            case SCREEN_CHAT: {
                String text = in.readString();
                if (!(cur instanceof GuiChat)) mc.displayGuiScreen(new GuiChat(text));
                break;
            }
            case SCREEN_PAUSE:
                in.readString();
                pauseIntegrated = in.readBoolean();
                pauseLan = in.readBoolean();
                if (!(cur instanceof GuiIngameMenu)) mc.displayGuiScreen(new GuiIngameMenu());
                break;
            default:
                break;
        }
    }

    private static void menu(Minecraft mc, byte[] data) throws IOException {
        if (mc.player == null) return;
        Container c = mc.player.openContainer;
        PacketBuffer b = new PacketBuffer(Unpooled.wrappedBuffer(data));
        try {
            int window = b.readVarInt();
            int n = b.readVarInt();
            boolean match = c != null && c.windowId == window;
            for (int k = 0; k < n; k++) {
                int slot = b.readVarInt();
                ItemStack s = b.readItemStack();
                if (match && slot >= 0 && slot < c.inventorySlots.size()) c.putStackInSlot(slot, s);
            }
            if (b.readBoolean()) {
                ItemStack carried = b.readItemStack();
                if (match) mc.player.inventory.setItemStack(carried);
            }
        } finally {
            b.release();
        }
    }
}
