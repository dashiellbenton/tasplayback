package dev.dbenton.tasplayback.fabric.mc12111;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Tracks the local player's inventory between ticks (HUD stream, INVENTORY). The client predicts some
 * changes (placing a block, eating) before the server confirms them, so packets alone would show the
 * HUD a tick or two late; recording the client's own view per tick keeps the hotbar exact.
 */
final class InventoryTracker {
    private ItemStack[] last = new ItemStack[0];

    void reset() { last = new ItemStack[0]; }

    /** Encoded changed slots since the last call, or null if nothing changed. Read-only on the inventory. */
    byte[] diff(Inventory inv, RegistryAccess registries) {
        int n = inv.getContainerSize();
        if (last.length != n) {
            ItemStack[] grown = new ItemStack[n];
            for (int i = 0; i < n; i++) grown[i] = i < last.length && last[i] != null ? last[i] : null;
            last = grown;
        }
        List<Integer> changed = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ItemStack s = inv.getItem(i);
            if (last[i] == null || !ItemStack.matches(s, last[i])) changed.add(i);
        }
        if (changed.isEmpty()) return null;
        RegistryFriendlyByteBuf b = new RegistryFriendlyByteBuf(Unpooled.buffer(64 * changed.size()), registries);
        try {
            b.writeVarInt(changed.size());
            for (int i : changed) {
                ItemStack s = inv.getItem(i);
                b.writeVarInt(i);
                ItemStack.OPTIONAL_STREAM_CODEC.encode(b, s);
                last[i] = s.copy();
            }
            byte[] out = new byte[b.readableBytes()];
            b.readBytes(out);
            return out;
        } finally {
            b.release();
        }
    }

    static void apply(Inventory inv, RegistryAccess registries, byte[] data) {
        RegistryFriendlyByteBuf b = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(data), registries);
        try {
            int count = b.readVarInt();
            for (int k = 0; k < count; k++) {
                int slot = b.readVarInt();
                ItemStack s = ItemStack.OPTIONAL_STREAM_CODEC.decode(b);
                if (slot >= 0 && slot < inv.getContainerSize()) inv.setItem(slot, s);
            }
        } finally {
            b.release();
        }
    }
}
