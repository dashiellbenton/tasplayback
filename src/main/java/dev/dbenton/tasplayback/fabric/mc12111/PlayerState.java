package dev.dbenton.tasplayback.fabric.mc12111;

import dev.dbenton.tasplayback.fabric.mc12111.mixin.ClientAvatarStateAccessor;
import dev.dbenton.tasplayback.fabric.mc12111.mixin.EntityAccessor;
import dev.dbenton.tasplayback.fabric.mc12111.mixin.GameRendererAccessor;
import dev.dbenton.tasplayback.fabric.mc12111.mixin.ItemInHandRendererAccessor;
import dev.dbenton.tasplayback.fabric.mc12111.mixin.LivingEntityAccessor;
import dev.dbenton.tasplayback.fabric.mc12111.mixin.LocalPlayerAccessor;
import dev.dbenton.tasplayback.fabric.mc12111.mixin.WalkAnimationStateAccessor;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;

/**
 * The local player's render-relevant state after a client tick (HUD stream, PLAYER_TICK, mc12111 payload v1).
 *
 * <p>Capture only reads fields. On playback the state is written back after every replay tick, so the
 * vanilla renderer interpolates between exactly the recorded old/new values with the recorded partial
 * tick: view bob, hurt tilt, hand sway, swing, equip animation, third-person body and limbs come out
 * the same as in the recording.
 */
final class PlayerState {
    private static final int VERSION = 1;

    private PlayerState() {}

    /** Last hand-renderer stacks written, so unchanged stacks cost one byte. Recorder-thread confined (main thread). */
    static final class StackMemo {
        ItemStack main = ItemStack.EMPTY, off = ItemStack.EMPTY;
        net.minecraft.core.BlockPos destroyPos;
    }

    static byte[] capture(Minecraft mc, LocalPlayer p, RegistryAccess registries, StackMemo memo) {
        RegistryFriendlyByteBuf b = new RegistryFriendlyByteBuf(Unpooled.buffer(256), registries);
        try {
            b.writeVarInt(VERSION);
            b.writeDouble(p.getX()); b.writeDouble(p.getY()); b.writeDouble(p.getZ());
            b.writeDouble(p.xo); b.writeDouble(p.yo); b.writeDouble(p.zo);
            b.writeDouble(p.xOld); b.writeDouble(p.yOld); b.writeDouble(p.zOld);
            b.writeFloat(p.getYRot()); b.writeFloat(p.getXRot()); b.writeFloat(p.yRotO); b.writeFloat(p.xRotO);
            b.writeFloat(p.yBodyRot); b.writeFloat(p.yBodyRotO); b.writeFloat(p.yHeadRot); b.writeFloat(p.yHeadRotO);
            b.writeFloat(p.xBob); b.writeFloat(p.yBob); b.writeFloat(p.xBobO); b.writeFloat(p.yBobO);
            ClientAvatarStateAccessor av = (ClientAvatarStateAccessor) p.avatarState();
            b.writeFloat(av.tasp$walkDist()); b.writeFloat(av.tasp$walkDistO()); b.writeFloat(av.tasp$bob()); b.writeFloat(av.tasp$bobO());
            b.writeDouble(av.tasp$xCloak()); b.writeDouble(av.tasp$yCloak()); b.writeDouble(av.tasp$zCloak()); // cape motion
            b.writeDouble(av.tasp$xCloakO()); b.writeDouble(av.tasp$yCloakO()); b.writeDouble(av.tasp$zCloakO());
            WalkAnimationStateAccessor wa = (WalkAnimationStateAccessor) p.walkAnimation;
            b.writeFloat(wa.tasp$speedOld()); b.writeFloat(wa.tasp$speed()); b.writeFloat(wa.tasp$position());
            b.writeFloat(p.attackAnim); b.writeFloat(p.oAttackAnim);
            b.writeBoolean(p.swinging); b.writeVarInt(p.swingTime);
            b.writeByte(p.swingingArm == null ? -1 : p.swingingArm.ordinal());
            b.writeVarInt(p.hurtTime); b.writeVarInt(p.hurtDuration); b.writeVarInt(p.deathTime);
            b.writeByte(p.getEntityData().get(EntityAccessor.tasp$sharedFlagsId()));
            b.writeByte(p.getEntityData().get(LivingEntityAccessor.tasp$livingFlagsId()));
            b.writeVarInt(p.getPose().ordinal());
            b.writeBoolean(p.onGround());
            b.writeVarInt(p.getAirSupply());
            LocalPlayerAccessor lp = (LocalPlayerAccessor) p;
            b.writeBoolean(lp.tasp$startedUsingItem());
            b.writeByte(p.isUsingItem() ? p.getUsedItemHand().ordinal() : -1);
            b.writeVarInt(((LivingEntityAccessor) p).tasp$useItemRemaining());
            b.writeFloat(p.portalEffectIntensity); b.writeFloat(p.oPortalEffectIntensity);
            LivingEntityAccessor le = (LivingEntityAccessor) p;
            b.writeFloat(le.tasp$swim()); b.writeFloat(le.tasp$swimO());
            b.writeVarInt(le.tasp$attackStrengthTicker()); // attack-cooldown indicator
            GameRendererAccessor gr = (GameRendererAccessor) mc.gameRenderer;
            b.writeFloat(gr.tasp$spinTime()); b.writeFloat(gr.tasp$spinSpeed());
            b.writeVarInt(p.getInventory().getSelectedSlot());
            ItemInHandRendererAccessor ih = (ItemInHandRendererAccessor) mc.gameRenderer.itemInHandRenderer;
            b.writeFloat(ih.tasp$main()); b.writeFloat(ih.tasp$mainO()); b.writeFloat(ih.tasp$off()); b.writeFloat(ih.tasp$offO());
            writeStack(b, ih.tasp$mainItem(), memo.main);
            memo.main = ih.tasp$mainItem().copy();
            writeStack(b, ih.tasp$offItem(), memo.off);
            memo.off = ih.tasp$offItem().copy();
            // own block-breaking progress (cracks); vanilla never sends a player their own progress
            int stage = mc.gameMode != null && mc.gameMode.isDestroying() ? mc.gameMode.getDestroyStage() : -1;
            b.writeVarInt(stage + 1);
            if (stage >= 0) b.writeBlockPos(((dev.dbenton.tasplayback.fabric.mc12111.mixin.MultiPlayerGameModeAccessor) mc.gameMode).tasp$destroyPos());
            byte[] out = new byte[b.readableBytes()];
            b.readBytes(out);
            return out;
        } finally {
            b.release();
        }
    }

    private static void writeStack(RegistryFriendlyByteBuf b, ItemStack s, ItemStack last) {
        if (ItemStack.matches(s, last)) {
            b.writeBoolean(false);
        } else {
            b.writeBoolean(true);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(b, s);
        }
    }

    private static ItemStack readStack(RegistryFriendlyByteBuf b, ItemStack last) {
        return b.readBoolean() ? ItemStack.OPTIONAL_STREAM_CODEC.decode(b) : last.copy();
    }

    /** Writes a recorded state back onto the replay player. */
    static void apply(Minecraft mc, LocalPlayer p, RegistryAccess registries, byte[] data, StackMemo memo) {
        RegistryFriendlyByteBuf b = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(data), registries);
        try {
            int v = b.readVarInt();
            if (v != VERSION) throw new IllegalStateException("unsupported player state version " + v);
            p.setPos(b.readDouble(), b.readDouble(), b.readDouble()); // also moves the bounding box (frustum culling)
            p.xo = b.readDouble(); p.yo = b.readDouble(); p.zo = b.readDouble();
            p.xOld = b.readDouble(); p.yOld = b.readDouble(); p.zOld = b.readDouble();
            p.setYRot(b.readFloat()); p.setXRot(b.readFloat()); p.yRotO = b.readFloat(); p.xRotO = b.readFloat();
            p.yBodyRot = b.readFloat(); p.yBodyRotO = b.readFloat(); p.yHeadRot = b.readFloat(); p.yHeadRotO = b.readFloat();
            p.xBob = b.readFloat(); p.yBob = b.readFloat(); p.xBobO = b.readFloat(); p.yBobO = b.readFloat();
            ClientAvatarStateAccessor av = (ClientAvatarStateAccessor) p.avatarState();
            av.tasp$setWalkDist(b.readFloat()); av.tasp$setWalkDistO(b.readFloat()); av.tasp$setBob(b.readFloat()); av.tasp$setBobO(b.readFloat());
            av.tasp$setxCloak(b.readDouble()); av.tasp$setyCloak(b.readDouble()); av.tasp$setzCloak(b.readDouble());
            av.tasp$setxCloakO(b.readDouble()); av.tasp$setyCloakO(b.readDouble()); av.tasp$setzCloakO(b.readDouble());
            WalkAnimationStateAccessor wa = (WalkAnimationStateAccessor) p.walkAnimation;
            wa.tasp$setSpeedOld(b.readFloat()); wa.tasp$setSpeed(b.readFloat()); wa.tasp$setPosition(b.readFloat());
            p.attackAnim = b.readFloat(); p.oAttackAnim = b.readFloat();
            p.swinging = b.readBoolean(); p.swingTime = b.readVarInt();
            int arm = b.readByte();
            p.swingingArm = arm < 0 ? null : InteractionHand.values()[arm];
            p.hurtTime = b.readVarInt(); p.hurtDuration = b.readVarInt(); p.deathTime = b.readVarInt();
            p.getEntityData().set(EntityAccessor.tasp$sharedFlagsId(), b.readByte());
            p.getEntityData().set(LivingEntityAccessor.tasp$livingFlagsId(), b.readByte());
            p.setPose(Pose.values()[b.readVarInt()]);
            p.setOnGround(b.readBoolean());
            p.setAirSupply(b.readVarInt());
            LocalPlayerAccessor lp = (LocalPlayerAccessor) p;
            lp.tasp$setStartedUsingItem(b.readBoolean());
            int usingHand = b.readByte();
            int remaining = b.readVarInt();
            LivingEntityAccessor le = (LivingEntityAccessor) p;
            if (usingHand >= 0) {
                InteractionHand hand = InteractionHand.values()[usingHand];
                lp.tasp$setUsingItemHand(hand);
                le.tasp$setUseItem(p.getItemInHand(hand));
            } else {
                le.tasp$setUseItem(ItemStack.EMPTY);
            }
            le.tasp$setUseItemRemaining(remaining);
            p.portalEffectIntensity = b.readFloat(); p.oPortalEffectIntensity = b.readFloat();
            le.tasp$setSwim(b.readFloat()); le.tasp$setSwimO(b.readFloat());
            le.tasp$setAttackStrengthTicker(b.readVarInt());
            GameRendererAccessor gr = (GameRendererAccessor) mc.gameRenderer;
            gr.tasp$setSpinTime(b.readFloat()); gr.tasp$setSpinSpeed(b.readFloat());
            p.getInventory().setSelectedSlot(b.readVarInt());
            ItemInHandRendererAccessor ih = (ItemInHandRendererAccessor) mc.gameRenderer.itemInHandRenderer;
            ih.tasp$setMain(b.readFloat()); ih.tasp$setMainO(b.readFloat()); ih.tasp$setOff(b.readFloat()); ih.tasp$setOffO(b.readFloat());
            memo.main = readStack(b, memo.main);
            ih.tasp$setMainItem(memo.main.copy());
            memo.off = readStack(b, memo.off);
            ih.tasp$setOffItem(memo.off.copy());
            int stage = b.readVarInt() - 1;
            if (stage >= 0) {
                net.minecraft.core.BlockPos pos = b.readBlockPos();
                if (memo.destroyPos != null && !memo.destroyPos.equals(pos)) mc.level.destroyBlockProgress(p.getId(), memo.destroyPos, -1);
                mc.level.destroyBlockProgress(p.getId(), pos, stage);
                memo.destroyPos = pos;
            } else if (memo.destroyPos != null) {
                mc.level.destroyBlockProgress(p.getId(), memo.destroyPos, -1);
                memo.destroyPos = null;
            }
        } finally {
            b.release();
        }
    }
}
