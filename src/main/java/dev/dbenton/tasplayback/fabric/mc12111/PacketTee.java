package dev.dbenton.tasplayback.fabric.mc12111;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.network.protocol.Packet;

import java.util.ArrayDeque;
import java.util.Iterator;

/**
 * Copies every inbound frame of the singleplayer connection (in 1.21.11 the in-memory pipeline carries
 * encoded packet bytes) and remembers which frame produced which decoded packet, so the main thread can
 * report when it handled it.
 *
 * <p>Runs on the connection's network thread. It only copies bytes and passes every message on
 * unchanged; it never alters, delays or reorders packets.
 */
final class PacketTee {
    interface Sink {
        void rawFrame(int segment, long seq, byte[] bytes);
    }

    private static final class Pending {
        final Packet<?> packet;
        final long seq;
        Pending(Packet<?> packet, long seq) { this.packet = packet; this.seq = seq; }
    }

    private final int segment;
    private final Sink sink;
    private long seq = -1; // network thread only
    private final ArrayDeque<Pending> pending = new ArrayDeque<>();

    PacketTee(int segment, Sink sink) {
        this.segment = segment;
        this.sink = sink;
    }

    int segment() { return segment; }

    void install(Channel channel) {
        channel.pipeline().addAfter("splitter", "tasplayback_tee", new ChannelInboundHandlerAdapter() {
            @Override public void channelRead(ChannelHandlerContext ctx, Object msg) {
                if (msg instanceof ByteBuf) {
                    Tripwire.enter();
                    try {
                    ByteBuf buf = (ByteBuf) msg;
                    byte[] copy = new byte[buf.readableBytes()];
                    buf.getBytes(buf.readerIndex(), copy);
                    seq++;
                    sink.rawFrame(segment, seq, copy);
                    } finally {
                        Tripwire.exit();
                    }
                }
                ctx.fireChannelRead(msg);
            }
        });
        channel.pipeline().addBefore("packet_handler", "tasplayback_mark", new ChannelInboundHandlerAdapter() {
            @Override public void channelRead(ChannelHandlerContext ctx, Object msg) {
                if (msg instanceof Packet) {
                    synchronized (pending) {
                        pending.addLast(new Pending((Packet<?>) msg, seq));
                        if (pending.size() > 100_000) pending.removeFirst(); // safety valve; never reached in practice
                    }
                }
                ctx.fireChannelRead(msg);
            }
        });
    }

    /**
     * Called on the main thread when a packet is handled there. Returns the sequence number of the last
     * frame it was built from, or -1 if the packet did not come through this connection. Entries older
     * than the handled packet were handled on the network thread and are dropped.
     */
    long takeSeq(Packet<?> packet) {
        synchronized (pending) {
            boolean present = false;
            for (Pending p : pending) if (p.packet == packet) { present = true; break; }
            if (!present) return -1;
            Iterator<Pending> it = pending.iterator();
            while (it.hasNext()) {
                Pending p = it.next();
                it.remove();
                if (p.packet == packet) return p.seq;
            }
            return -1;
        }
    }
}
