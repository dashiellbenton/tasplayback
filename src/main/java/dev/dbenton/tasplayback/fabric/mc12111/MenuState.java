package dev.dbenton.tasplayback.fabric.mc12111;

import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The open menu's slots and the stack on the cursor as the client saw them after each frame (GUI stream,
 * MENU_STATE). Clicks change these client-side before the server confirms; restoring them during playback
 * keeps the replayed screen exactly as recorded even if replayed input diverged.
 */
final class MenuState {
    private int lastId = Integer.MIN_VALUE;
    private ItemStack[] last = new ItemStack[0];
    private ItemStack lastCarried = ItemStack.EMPTY;

    void reset() { lastId = Integer.MIN_VALUE; last = new ItemStack[0]; lastCarried = ItemStack.EMPTY; }

    /** Changes since the previous frame, or null. Read-only on the menu. */
    byte[] capture(AbstractContainerMenu menu, RegistryAccess registries) {
        NonNullList<ItemStack> items = menu.getItems();
        boolean full = menu.containerId != lastId || last.length != items.size();
        List<Integer> changed = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) if (full || !ItemStack.matches(items.get(i), last[i])) changed.add(i);
        ItemStack carried = menu.getCarried();
        boolean carriedChanged = full || !ItemStack.matches(carried, lastCarried);
        if (changed.isEmpty() && !carriedChanged) return null;
        RegistryFriendlyByteBuf b = new RegistryFriendlyByteBuf(Unpooled.buffer(64), registries);
        try {
            b.writeVarInt(menu.containerId);
            b.writeVarInt(changed.size());
            if (full) last = new ItemStack[items.size()];
            for (int i : changed) {
                b.writeVarInt(i);
                ItemStack.OPTIONAL_STREAM_CODEC.encode(b, items.get(i));
                last[i] = items.get(i).copy();
            }
            b.writeBoolean(carriedChanged);
            if (carriedChanged) {
                ItemStack.OPTIONAL_STREAM_CODEC.encode(b, carried);
                lastCarried = carried.copy();
            }
            lastId = menu.containerId;
            byte[] out = new byte[b.readableBytes()];
            b.readBytes(out);
            return out;
        } finally {
            b.release();
        }
    }

    void apply(Minecraft mc, byte[] data) {
        if (mc.player == null || mc.getConnection() == null) return;
        AbstractContainerMenu menu = mc.player.containerMenu;
        RegistryFriendlyByteBuf b = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(data), mc.getConnection().registryAccess());
        try {
            int id = b.readVarInt();
            int n = b.readVarInt();
            boolean match = menu != null && menu.containerId == id;
            for (int k = 0; k < n; k++) {
                int slot = b.readVarInt();
                ItemStack s = ItemStack.OPTIONAL_STREAM_CODEC.decode(b);
                if (match && slot >= 0 && slot < menu.slots.size()) menu.getSlot(slot).set(s);
            }
            if (b.readBoolean()) {
                ItemStack carried = ItemStack.OPTIONAL_STREAM_CODEC.decode(b);
                if (match) menu.setCarried(carried);
            }
        } finally {
            b.release();
        }
    }
}
