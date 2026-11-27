package dev.dbenton.tasplayback.fabric.mc12111;

import dev.dbenton.tasplayback.fabric.mc12111.mixin.MinecraftAccessor;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;

/**
 * A client connection with no server behind it. It uses the same in-memory pipeline as singleplayer, on a
 * netty {@link EmbeddedChannel} that runs on the calling (main) thread. Recorded inbound frames are fed in;
 * whatever the client sends is discarded. Login, configuration and play-phase switches happen exactly as
 * in the recorded session because the vanilla listeners drive them.
 */
final class ReplayConnection {
    final Connection connection;
    final EmbeddedChannel channel;

    private ReplayConnection(Connection connection, EmbeddedChannel channel) {
        this.connection = connection;
        this.channel = channel;
    }

    static ReplayConnection open(Minecraft mc) {
        final Connection c = new Connection(PacketFlow.CLIENTBOUND);
        EmbeddedChannel ch = new EmbeddedChannel(new ChannelInitializer<Channel>() {
            @Override protected void initChannel(Channel channel) {
                Connection.configureInMemoryPipeline(channel.pipeline(), PacketFlow.CLIENTBOUND);
                c.configurePacketHandler(channel.pipeline());
            }
        });
        c.initiateServerboundPlayConnection("tasplayback-replay", 0,
                new ClientHandshakePacketListenerImpl(c, mc, null, null, false, null, component -> {}, new LevelLoadTracker(), null));
        c.send(new ServerboundHelloPacket(mc.getUser().getName(), mc.getUser().getProfileId()));
        ((MinecraftAccessor) mc).tasp$setPendingConnection(c);
        ReplayConnection rc = new ReplayConnection(c, ch);
        rc.drainOutbound();
        return rc;
    }

    /** Feeds one recorded inbound frame; the packet is decoded and handled immediately on this thread. */
    void feed(byte[] frame) {
        channel.writeInbound(Unpooled.wrappedBuffer(frame));
        channel.runPendingTasks();
        Object leftover;
        while ((leftover = channel.readInbound()) != null) ReferenceCountUtil.release(leftover);
    }

    /** Discards everything the replay client tried to send. */
    void drainOutbound() {
        channel.runPendingTasks();
        Object o;
        while ((o = channel.readOutbound()) != null) ReferenceCountUtil.release(o);
    }

    void close() {
        if (channel.isOpen()) channel.close();
        channel.runPendingTasks();
        drainOutbound();
    }
}
