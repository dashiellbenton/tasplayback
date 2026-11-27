package dev.dbenton.tasplayback.legacy;

import dev.dbenton.tasplayback.legacy.mixin.EntityAccessor;
import dev.dbenton.tasplayback.legacy.mixin.EntityLivingBaseAccessor;
import dev.dbenton.tasplayback.legacy.mixin.EntityPlayerSPAccessor;
import dev.dbenton.tasplayback.legacy.mixin.ItemRendererAccessor;
import dev.dbenton.tasplayback.legacy.mixin.PlayerControllerMPAccessor;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;

/**
 * 1.12.2 local-player render state after a client tick (HUD stream, PLAYER_TICK, legacy payload v1).
 * Captured read-only; restored after every replay tick so vanilla interpolation reproduces the frame.
 */
final class LegacyPlayerState {
    private static final int VERSION = 1;

    private LegacyPlayerState() {}

    static final class Memo {
        ItemStack main = ItemStack.EMPTY, off = ItemStack.EMPTY;
        BlockPos destroyPos;
    }

    static byte[] capture(Minecraft mc, EntityPlayerSP p, Memo memo) {
        PacketBuffer b = new PacketBuffer(Unpooled.buffer(256));
        try {
            b.writeVarInt(VERSION);
            b.writeDouble(p.posX); b.writeDouble(p.posY); b.writeDouble(p.posZ);
            b.writeDouble(p.prevPosX); b.writeDouble(p.prevPosY); b.writeDouble(p.prevPosZ);
            b.writeDouble(p.lastTickPosX); b.writeDouble(p.lastTickPosY); b.writeDouble(p.lastTickPosZ);
            b.writeFloat(p.rotationYaw); b.writeFloat(p.rotationPitch); b.writeFloat(p.prevRotationYaw); b.writeFloat(p.prevRotationPitch);
            b.writeFloat(p.renderYawOffset); b.writeFloat(p.prevRenderYawOffset); b.writeFloat(p.rotationYawHead); b.writeFloat(p.prevRotationYawHead);
            b.writeFloat(p.renderArmYaw); b.writeFloat(p.renderArmPitch); b.writeFloat(p.prevRenderArmYaw); b.writeFloat(p.prevRenderArmPitch);
            b.writeFloat(p.distanceWalkedModified); b.writeFloat(p.prevDistanceWalkedModified);
            b.writeFloat(p.cameraYaw); b.writeFloat(p.prevCameraYaw); b.writeFloat(p.cameraPitch); b.writeFloat(p.prevCameraPitch);
            b.writeFloat(p.limbSwing); b.writeFloat(p.limbSwingAmount); b.writeFloat(p.prevLimbSwingAmount);
            b.writeDouble(p.chasingPosX); b.writeDouble(p.chasingPosY); b.writeDouble(p.chasingPosZ); // cape motion
            b.writeDouble(p.prevChasingPosX); b.writeDouble(p.prevChasingPosY); b.writeDouble(p.prevChasingPosZ);
            b.writeFloat(p.swingProgress); b.writeFloat(p.prevSwingProgress);
            b.writeBoolean(p.isSwingInProgress); b.writeVarInt(p.swingProgressInt);
            b.writeByte(p.swingingHand == null ? -1 : p.swingingHand.ordinal());
            b.writeVarInt(p.hurtTime); b.writeVarInt(p.maxHurtTime); b.writeFloat(p.attackedAtYaw); b.writeVarInt(p.deathTime);
            b.writeByte(p.getDataManager().get(EntityAccessor.tasp$flags()));
            b.writeByte(p.getDataManager().get(EntityLivingBaseAccessor.tasp$handStates()));
            b.writeBoolean(p.onGround);
            b.writeVarInt(p.getAir());
            b.writeBoolean(((EntityPlayerSPAccessor) p).tasp$handActive());
            EntityLivingBaseAccessor le = (EntityLivingBaseAccessor) p;
            b.writeVarInt(le.tasp$useCount());
            b.writeVarInt(le.tasp$ticksSinceLastSwing());
            b.writeFloat(p.timeInPortal); b.writeFloat(p.prevTimeInPortal);
            b.writeVarInt(p.inventory.currentItem);
            ItemRendererAccessor ir = (ItemRendererAccessor) mc.getItemRenderer();
            b.writeFloat(ir.tasp$eqMain()); b.writeFloat(ir.tasp$eqMainO()); b.writeFloat(ir.tasp$eqOff()); b.writeFloat(ir.tasp$eqOffO());
            writeStack(b, ir.tasp$main(), memo.main);
            memo.main = ir.tasp$main().copy();
            writeStack(b, ir.tasp$off(), memo.off);
            memo.off = ir.tasp$off().copy();
            PlayerControllerMPAccessor pc = mc.playerController == null ? null : (PlayerControllerMPAccessor) mc.playerController;
            int stage = pc != null && mc.playerController.getIsHittingBlock() ? (int) (pc.tasp$damage() * 10.0F) - 1 : -1;
            b.writeVarInt(stage + 1);
            if (stage >= 0) b.writeBlockPos(pc.tasp$currentBlock());
            byte[] out = new byte[b.readableBytes()];
            b.readBytes(out);
            return out;
        } finally {
            b.release();
        }
    }

    private static void writeStack(PacketBuffer b, ItemStack s, ItemStack last) {
        if (ItemStack.areItemStacksEqual(s, last)) {
            b.writeBoolean(false);
        } else {
            b.writeBoolean(true);
            b.writeItemStack(s);
        }
    }

    private static ItemStack readStack(PacketBuffer b, ItemStack last) throws IOException {
        return b.readBoolean() ? b.readItemStack() : last.copy();
    }

    static void apply(Minecraft mc, EntityPlayerSP p, byte[] data, Memo memo) throws IOException {
        PacketBuffer b = new PacketBuffer(Unpooled.wrappedBuffer(data));
        try {
            int v = b.readVarInt();
            if (v != VERSION) throw new IOException("unsupported player state version " + v);
            p.setPosition(b.readDouble(), b.readDouble(), b.readDouble()); // also moves the bounding box
            p.prevPosX = b.readDouble(); p.prevPosY = b.readDouble(); p.prevPosZ = b.readDouble();
            p.lastTickPosX = b.readDouble(); p.lastTickPosY = b.readDouble(); p.lastTickPosZ = b.readDouble();
            p.rotationYaw = b.readFloat(); p.rotationPitch = b.readFloat(); p.prevRotationYaw = b.readFloat(); p.prevRotationPitch = b.readFloat();
            p.renderYawOffset = b.readFloat(); p.prevRenderYawOffset = b.readFloat(); p.rotationYawHead = b.readFloat(); p.prevRotationYawHead = b.readFloat();
            p.renderArmYaw = b.readFloat(); p.renderArmPitch = b.readFloat(); p.prevRenderArmYaw = b.readFloat(); p.prevRenderArmPitch = b.readFloat();
            p.distanceWalkedModified = b.readFloat(); p.prevDistanceWalkedModified = b.readFloat();
            p.cameraYaw = b.readFloat(); p.prevCameraYaw = b.readFloat(); p.cameraPitch = b.readFloat(); p.prevCameraPitch = b.readFloat();
            p.limbSwing = b.readFloat(); p.limbSwingAmount = b.readFloat(); p.prevLimbSwingAmount = b.readFloat();
            p.chasingPosX = b.readDouble(); p.chasingPosY = b.readDouble(); p.chasingPosZ = b.readDouble();
            p.prevChasingPosX = b.readDouble(); p.prevChasingPosY = b.readDouble(); p.prevChasingPosZ = b.readDouble();
            p.swingProgress = b.readFloat(); p.prevSwingProgress = b.readFloat();
            p.isSwingInProgress = b.readBoolean(); p.swingProgressInt = b.readVarInt();
            int hand = b.readByte();
            p.swingingHand = hand < 0 ? null : EnumHand.values()[hand];
            p.hurtTime = b.readVarInt(); p.maxHurtTime = b.readVarInt(); p.attackedAtYaw = b.readFloat(); p.deathTime = b.readVarInt();
            p.getDataManager().set(EntityAccessor.tasp$flags(), b.readByte());
            byte handStates = b.readByte();
            p.getDataManager().set(EntityLivingBaseAccessor.tasp$handStates(), handStates);
            p.onGround = b.readBoolean();
            p.setAir(b.readVarInt());
            ((EntityPlayerSPAccessor) p).tasp$setHandActive(b.readBoolean());
            EntityLivingBaseAccessor le = (EntityLivingBaseAccessor) p;
            int useCount = b.readVarInt();
            if ((handStates & 1) != 0) {
                EnumHand active = (handStates & 2) != 0 ? EnumHand.OFF_HAND : EnumHand.MAIN_HAND;
                le.tasp$setActiveItemStack(p.getHeldItem(active));
            } else {
                le.tasp$setActiveItemStack(ItemStack.EMPTY);
            }
            le.tasp$setUseCount(useCount);
            le.tasp$setTicksSinceLastSwing(b.readVarInt());
            p.timeInPortal = b.readFloat(); p.prevTimeInPortal = b.readFloat();
            p.inventory.currentItem = b.readVarInt();
            ItemRendererAccessor ir = (ItemRendererAccessor) mc.getItemRenderer();
            ir.tasp$setEqMain(b.readFloat()); ir.tasp$setEqMainO(b.readFloat()); ir.tasp$setEqOff(b.readFloat()); ir.tasp$setEqOffO(b.readFloat());
            memo.main = readStack(b, memo.main);
            ir.tasp$setMain(memo.main.copy());
            memo.off = readStack(b, memo.off);
            ir.tasp$setOff(memo.off.copy());
            int stage = b.readVarInt() - 1;
            if (stage >= 0) {
                BlockPos pos = b.readBlockPos();
                if (memo.destroyPos != null && !memo.destroyPos.equals(pos)) mc.world.sendBlockBreakProgress(p.getEntityId(), memo.destroyPos, -1);
                mc.world.sendBlockBreakProgress(p.getEntityId(), pos, stage);
                memo.destroyPos = pos;
            } else if (memo.destroyPos != null) {
                mc.world.sendBlockBreakProgress(p.getEntityId(), memo.destroyPos, -1);
                memo.destroyPos = null;
            }
        } finally {
            b.release();
        }
    }
}
