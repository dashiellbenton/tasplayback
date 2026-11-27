package dev.dbenton.tasplayback.legacy;

import dev.dbenton.tasplayback.core.json.Json;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Dev-only scripted session for 1.12.2 (see the 1.21.11 DevTest); inert unless -Dtasplayback.devtest is set. */
final class LegacyDevTest {
    static final String LABEL = System.getProperty("tasplayback.devtest");
    private static final String WORLD = "tasp-devtest";
    private static final int END_TICK = 300;
    private static int phase, t, waited, settle;

    private LegacyDevTest() {}

    static boolean active() { return LABEL != null; }
    static boolean done() { return phase == 3; }

    static void tick(Minecraft mc) {
        if (!active() || LegacyClient.get().isReplaying()) return;
        switch (phase) {
            case 0:
                if (mc.currentScreen != null && mc.world == null && ++waited > 40) createWorld(mc);
                break;
            case 1:
                if (mc.player != null && mc.world != null && mc.currentScreen == null) { phase = 2; t = 0; }
                break;
            case 2:
                // after the /tp at tick 2, hold the script until the client has actually received the teleport,
                // so the scripted input starts from the same position regardless of server-thread timing
                if (t == 3 && (Math.abs(mc.player.posX - 0.5) > 1e-9 || Math.abs(mc.player.posZ - 0.5) > 1e-9 || ++settle < 5)) break;
                script(mc, mc.player, t++);
                break;
            default:
                if (mc.world == null && mc.currentScreen != null && System.getProperty("tasplayback.autoplay") == null) mc.shutdown();
                break;
        }
    }

    private static void createWorld(Minecraft mc) {
        phase = 1;
        mc.gameSettings.tutorialStep = TutorialSteps.NONE;
        mc.gameSettings.pauseOnLostFocus = false;
        mc.getSaveLoader().deleteWorldDirectory(WORLD);
        mc.launchIntegratedServer(WORLD, WORLD, new WorldSettings(20261127L, GameType.SURVIVAL, false, false, WorldType.FLAT).enableCommands());
    }

    private static void key(KeyBinding k, boolean down) { KeyBinding.setKeyBindState(k.getKeyCode(), down); }

    private static void click(KeyBinding k) { KeyBinding.onTick(k.getKeyCode()); }

    private static void script(Minecraft mc, EntityPlayerSP p, int tick) {
        GameSettings o = mc.gameSettings;
        if (tick == 1) mc.setIngameFocus();
        if (tick == 2) p.sendChatMessage("/tp @p 0.5 4 0.5 0 0");
        if (tick == 5) p.sendChatMessage("/give @p minecraft:stone 32");
        if (tick == 6) p.sendChatMessage("/give @p minecraft:planks 16");
        if (tick == 7) p.sendChatMessage("/time set 6000");
        key(o.keyBindForward, tick >= 20 && tick < 90);
        if (tick >= 30 && tick < 70) p.turn(6.0F, 0.0F);
        key(o.keyBindJump, tick >= 60 && tick < 64);
        key(o.keyBindLeft, tick >= 80 && tick < 100);
        if (tick == 100 || tick == 104 || tick == 108) click(o.keyBindsHotbar[(tick - 100) / 4]);
        if (tick >= 110 && tick < 130) p.turn(0.0F, -12.0F); // 1.12.2 Entity.turn subtracts pitch: negative = look down
        key(o.keyBindAttack, tick >= 130 && tick < 175);
        if (tick == 180) click(o.keyBindsHotbar[0]);
        if (tick == 185 || tick == 195) click(o.keyBindUseItem);
        if (tick >= 200 && tick < 215) p.turn(-4.0F, 10.0F);
        if (tick == 220) o.thirdPersonView = 1;
        if (tick == 245) o.thirdPersonView = 2;
        if (tick == 270) o.thirdPersonView = 0;
        key(o.keyBindBack, tick >= 250 && tick < 270);
        if (tick == END_TICK) finish(mc, p);
    }

    private static void finish(Minecraft mc, EntityPlayerSP p) {
        phase = 3;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", LABEL);
        m.put("recording", LegacyClient.get().isRecording());
        m.put("x", p.posX); m.put("y", p.posY); m.put("z", p.posZ);
        m.put("yRot", (double) p.rotationYaw); m.put("xRot", (double) p.rotationPitch);
        m.put("selectedSlot", (long) p.inventory.currentItem);
        List<Object> inv = new ArrayList<>();
        for (int i = 0; i < p.inventory.getSizeInventory(); i++) {
            ItemStack s = p.inventory.getStackInSlot(i);
            if (!s.isEmpty()) inv.add(i + ":" + Item.REGISTRY.getNameForObject(s.getItem()) + "x" + s.getCount());
        }
        m.put("inventory", inv);
        m.put("health", (double) p.getHealth());
        m.put("food", (long) p.getFoodStats().getFoodLevel());
        m.put("levelGameTime", mc.world.getTotalWorldTime());
        m.put("tripwireViolations", -1L); // not measured: 1.12.2 uses java.util.Random, which cannot be instrumented by a mixin
        try {
            Path out = TASPlaybackLegacy.glue().gameDir().resolve("tasplayback").resolve("devtest-" + LABEL + ".json");
            Files.createDirectories(out.getParent());
            Files.write(out, Json.write(m).getBytes(StandardCharsets.UTF_8));
            TASPlaybackLegacy.LOGGER.info("devtest: wrote {}", out);
        } catch (IOException e) {
            TASPlaybackLegacy.LOGGER.error("devtest: could not write state", e);
        }
        for (KeyBinding k : mc.gameSettings.keyBindings) KeyBinding.setKeyBindState(k.getKeyCode(), false);
        mc.addScheduledTask(() -> {
            if (LegacyClient.get().isRecording()) LegacyClient.get().stopRecordingBlocking();
            mc.world.sendQuittingDisconnectingPacket();
            mc.loadWorld(null);
            mc.displayGuiScreen(new GuiMainMenu());
        });
    }
}
