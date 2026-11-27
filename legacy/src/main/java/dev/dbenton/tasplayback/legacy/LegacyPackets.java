package dev.dbenton.tasplayback.legacy;

import io.netty.buffer.Unpooled;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.SPacketCustomPayload;

import java.io.IOException;

/**
 * Play-phase packet (de)serialization with the game's own codecs. In 1.12.2 the singleplayer channel
 * passes packet objects, so the recorder encodes each packet when the main thread handles it.
 */
final class LegacyPackets {
    private LegacyPackets() {}

    /** Encodes {@code [varint id][payload]}, or returns null for packets outside the play protocol. Read-only on the packet. */
    static byte[] encode(Packet<?> packet) throws IOException {
        Integer id;
        try {
            id = EnumConnectionState.PLAY.getPacketId(EnumPacketDirection.CLIENTBOUND, packet);
        } catch (Exception e) { // Forge declares "throws Exception"
            return null;
        }
        if (id == null) return null;
        Packet<?> toWrite = packet;
        if (packet instanceof SPacketCustomPayload) {
            // writing a custom payload consumes its buffer; encode a copy so the real handler still sees the data
            SPacketCustomPayload p = (SPacketCustomPayload) packet;
            toWrite = new SPacketCustomPayload(p.getChannelName(), new PacketBuffer(p.getBufferData().copy()));
        }
        PacketBuffer b = new PacketBuffer(Unpooled.buffer(256));
        try {
            b.writeVarInt(id);
            toWrite.writePacketData(b);
            byte[] out = new byte[b.readableBytes()];
            b.readBytes(out);
            return out;
        } finally {
            b.release();
        }
    }

    static Packet<?> decode(byte[] data) throws IOException {
        PacketBuffer b = new PacketBuffer(Unpooled.wrappedBuffer(data));
        int id = b.readVarInt();
        Packet<?> p;
        try {
            p = EnumConnectionState.PLAY.getPacket(EnumPacketDirection.CLIENTBOUND, id);
        } catch (InstantiationException | IllegalAccessException e) {
            throw new IOException("cannot create packet " + id, e);
        }
        if (p == null) throw new IOException("unknown clientbound packet id " + id);
        p.readPacketData(b);
        return p;
    }
}
