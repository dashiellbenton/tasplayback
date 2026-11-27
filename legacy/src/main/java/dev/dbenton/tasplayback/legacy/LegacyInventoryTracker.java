package dev.dbenton.tasplayback.legacy;

import io.netty.buffer.Unpooled;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Per-tick inventory changes as the client saw them (HUD stream, INVENTORY, legacy payload). */
final class LegacyInventoryTracker {
    private ItemStack[] last = new ItemStack[0];

    void reset() { last = new ItemStack[0]; }

    byte[] diff(InventoryPlayer inv) {
        int n = inv.getSizeInventory();
        if (last.length != n) last = new ItemStack[n];
        List<Integer> changed = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (last[i] == null || !ItemStack.areItemStacksEqual(inv.getStackInSlot(i), last[i])) changed.add(i);
        }
        if (changed.isEmpty()) return null;
        PacketBuffer b = new PacketBuffer(Unpooled.buffer(64 * changed.size()));
        try {
            b.writeVarInt(changed.size());
            for (int i : changed) {
                ItemStack s = inv.getStackInSlot(i);
                b.writeVarInt(i);
                b.writeItemStack(s);
                last[i] = s.copy();
            }
            byte[] out = new byte[b.readableBytes()];
            b.readBytes(out);
            return out;
        } finally {
            b.release();
        }
    }

    static void apply(InventoryPlayer inv, byte[] data) throws IOException {
        PacketBuffer b = new PacketBuffer(Unpooled.wrappedBuffer(data));
        try {
            int count = b.readVarInt();
            for (int k = 0; k < count; k++) {
                int slot = b.readVarInt();
                ItemStack s = b.readItemStack();
                if (slot >= 0 && slot < inv.getSizeInventory()) inv.setInventorySlotContents(slot, s);
            }
        } finally {
            b.release();
        }
    }
}
