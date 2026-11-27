package dev.dbenton.tasplayback.fabric.mc12111;

import dev.dbenton.tasplayback.core.json.Json;
import dev.dbenton.tasplayback.fabric.TASPlaybackFabric;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Dev-only scripted session, active only with {@code -Dtasplayback.devtest=<label>} (inert otherwise).
 *
 * <p>Creates a fresh fixed-seed superflat world, plays a fixed input script driven by client ticks (walk,
 * turn, jump, hotbar changes, block breaking/placing, perspective changes), dumps the resulting
 * player/inventory state to {@code tasplayback/devtest-<label>.json}, then leaves the world. With
 * {@code -Dtasplayback.autoplay=latest} the fresh recording is then replayed (see TaspClient).
 */
final class DevTest {
    static final String LABEL = System.getProperty("tasplayback.devtest");
    private static final String WORLD = "tasp-devtest";
    private static final int END_TICK = 300;

    private static int phase; // 0 = waiting for title, 1 = world requested, 2 = in world, 3 = done
    private static int t;
    private static int waited;
    private static int settle;

    private DevTest() {}

    static boolean active() { return LABEL != null; }

    static boolean done() { return phase == 3; }

    /** Called at the start of every client tick (before vanilla handles key bindings). */
    static void tick(Minecraft mc) {
        if (!active() || TaspClient.get().isReplaying()) return;
        switch (phase) {
            case 0:
                // any menu (title screen, or the first-launch accessibility onboarding screen)
                if (mc.screen != null && mc.level == null && mc.getOverlay() == null && ++waited > 40) createWorld(mc);
                break;
            case 1:
                if (mc.player != null && mc.level != null && mc.screen == null && mc.player.connection.hasClientLoaded()) { phase = 2; t = 0; }
                break;
            case 2:
                // hold the script until the client has received the /tp from tick 2 (server-thread timing varies)
                if (t == 3 && (Math.abs(mc.player.getX() - 0.5) > 1e-9 || Math.abs(mc.player.getZ() - 0.5) > 1e-9 || ++settle < 5)) break;
                script(mc, mc.player, t++);
                break;
            default:
                // back at a menu after the script: quit unless a replay was requested
                if (mc.level == null && mc.getConnection() == null && mc.screen != null && System.getProperty("tasplayback.autoplay") == null) mc.stop();
                break;
        }
    }

    private static void createWorld(Minecraft mc) {
        phase = 1;
        // tutorial hints react to live input and animate on wall-clock time; keep them out of the comparison
        mc.options.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
        try {
            Path dir = mc.getLevelSource().getBaseDir().resolve(WORLD);
            if (Files.exists(dir)) deleteTree(dir);
        } catch (IOException e) {
            TASPlaybackFabric.LOGGER.error("devtest: could not delete old world", e);
        }
        LevelSettings settings = new LevelSettings(WORLD, GameType.SURVIVAL, false, Difficulty.PEACEFUL, true,
                new GameRules(WorldDataConfiguration.DEFAULT.enabledFeatures()), WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(20261127L, false, false),
                WorldPresets::createFlatWorldDimensions, null);
    }

    private static void key(KeyMapping k, boolean down) { k.setDown(down); }

    private static void click(KeyMapping k) {
        KeyMapping.click(((dev.dbenton.tasplayback.fabric.mc12111.mixin.KeyMappingAccessor) k).tasp$key());
    }

    /** The scripted input. Only touches input state, like a player would. */
    private static void script(Minecraft mc, LocalPlayer p, int tick) {
        var o = mc.options;
        if (tick == 2) p.connection.sendCommand("tp @s 0.5 -60 0.5 0 0"); // vanilla randomizes the spawn point
        if (tick == 5) p.connection.sendCommand("give @s minecraft:stone 32");
        if (tick == 6) p.connection.sendCommand("give @s minecraft:oak_planks 16");
        if (tick == 7) p.connection.sendCommand("time set 6000");
        key(o.keyUp, tick >= 20 && tick < 90);
        if (tick >= 30 && tick < 70) p.turn(6.0, 0.0);
        key(o.keyJump, tick >= 60 && tick < 64);
        key(o.keyLeft, tick >= 80 && tick < 100);
        if (tick == 100 || tick == 104 || tick == 108) click(o.keyHotbarSlots[(tick - 100) / 4]);
        if (tick >= 110 && tick < 130) p.turn(0.0, 12.0); // look down
        key(o.keyAttack, tick >= 130 && tick < 175);       // break a block
        if (tick == 180) click(o.keyHotbarSlots[0]);
        if (tick == 185 || tick == 195) click(o.keyUse);   // place stone
        if (tick >= 200 && tick < 215) p.turn(-4.0, -10.0);
        if (tick == 220) o.setCameraType(CameraType.THIRD_PERSON_BACK);
        if (tick == 245) o.setCameraType(CameraType.THIRD_PERSON_FRONT);
        if (tick == 270) o.setCameraType(CameraType.FIRST_PERSON);
        if (tick >= 250 && tick < 270) key(o.keyDown, true); else key(o.keyDown, false);
        if (tick == END_TICK) finish(mc, p);
    }

    private static void finish(Minecraft mc, LocalPlayer p) {
        phase = 3;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", LABEL);
        m.put("recording", TaspClient.get().isRecording());
        m.put("x", p.getX()); m.put("y", p.getY()); m.put("z", p.getZ());
        m.put("yRot", (double) p.getYRot()); m.put("xRot", (double) p.getXRot());
        m.put("selectedSlot", (long) p.getInventory().getSelectedSlot());
        List<Object> inv = new ArrayList<>();
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (!s.isEmpty()) inv.add(i + ":" + BuiltInRegistries.ITEM.getKey(s.getItem()) + "x" + s.getCount());
        }
        m.put("inventory", inv);
        m.put("health", (double) p.getHealth());
        m.put("food", (long) p.getFoodData().getFoodLevel());
        m.put("levelGameTime", mc.level.getGameTime());
        m.put("tripwireViolations", Tripwire.violations());
        try {
            Path out = mc.gameDirectory.toPath().resolve("tasplayback").resolve("devtest-" + LABEL + ".json");
            Files.createDirectories(out.getParent());
            Files.write(out, Json.write(m).getBytes(StandardCharsets.UTF_8));
            TASPlaybackFabric.LOGGER.info("devtest: wrote {}", out);
        } catch (IOException e) {
            TASPlaybackFabric.LOGGER.error("devtest: could not write state", e);
        }
        for (KeyMapping k : mc.options.keyMappings) k.setDown(false);
        mc.execute(() -> {
            if (TaspClient.get().isRecording()) TaspClient.get().stopRecordingBlocking();
            mc.disconnectFromWorld(net.minecraft.network.chat.Component.literal("devtest finished"));
        });
    }

    private static void deleteTree(Path dir) throws IOException {
        try (Stream<Path> s = Files.walk(dir)) {
            List<Path> all = new ArrayList<>();
            s.forEach(all::add);
            all.sort(Comparator.reverseOrder());
            for (Path p : all) Files.delete(p);
        }
    }
}
