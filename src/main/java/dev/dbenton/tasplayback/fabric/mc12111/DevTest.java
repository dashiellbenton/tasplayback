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
    private static final int END_TICK = 640;

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
        if (tick >= 300) gui(mc, tick - 300);
        if (tick == END_TICK) finish(mc, p);
    }

    // ------------------------------------------------------------------ GUI part (M3)

    private static double curX = 400, curY = 240, fromX, fromY, toX, toY;
    private static int moveStart, moveLen;

    private static long win(Minecraft mc) { return mc.getWindow().handle(); }

    private static double sx(Minecraft mc) { return (double) mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth(); }

    /** Starts a smooth cursor move to GUI coordinates (gx, gy) over n ticks. */
    private static void moveTo(Minecraft mc, int t, double gx, double gy, int n) {
        fromX = curX; fromY = curY; toX = gx * sx(mc); toY = gy * sx(mc); moveStart = t; moveLen = n;
    }

    private static void stepMouse(Minecraft mc, int t) {
        if (moveLen <= 0 || t < moveStart) return;
        double a = Math.min(1.0, (t - moveStart + 1) / (double) moveLen);
        curX = fromX + (toX - fromX) * a; curY = fromY + (toY - fromY) * a;
        ((dev.dbenton.tasplayback.fabric.mc12111.mixin.MouseHandlerInvoker) mc.mouseHandler).tasp$onMove(win(mc), curX, curY);
        if (a >= 1.0) moveLen = 0;
    }

    private static void mouse(Minecraft mc, int button, int action) {
        ((dev.dbenton.tasplayback.fabric.mc12111.mixin.MouseHandlerInvoker) mc.mouseHandler)
                .tasp$onButton(win(mc), new net.minecraft.client.input.MouseButtonInfo(button, 0), action);
    }

    private static void keyEvent(Minecraft mc, int key, int action) {
        ((dev.dbenton.tasplayback.fabric.mc12111.mixin.KeyboardHandlerInvoker) mc.keyboardHandler)
                .tasp$keyPress(win(mc), action, new net.minecraft.client.input.KeyEvent(key, 0, 0));
    }

    private static void type(Minecraft mc, char c) {
        ((dev.dbenton.tasplayback.fabric.mc12111.mixin.KeyboardHandlerInvoker) mc.keyboardHandler)
                .tasp$charTyped(win(mc), new net.minecraft.client.input.CharacterEvent(c, 0));
    }

    /** GUI coordinates of the centre of menu slot i in the open container screen. */
    private static double[] slot(Minecraft mc, int i) {
        var s = (net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) mc.screen;
        var a = (dev.dbenton.tasplayback.fabric.mc12111.mixin.ContainerScreenAccessor) s;
        var sl = s.getMenu().getSlot(i);
        return new double[] { a.tasp$left() + sl.x + 8, a.tasp$top() + sl.y + 8 };
    }

    private static void gui(Minecraft mc, int t) {
        var o = mc.options;
        stepMouse(mc, t);
        boolean inv = mc.screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen;
        if (t == 0) KeyMapping.click(((dev.dbenton.tasplayback.fabric.mc12111.mixin.KeyMappingAccessor) o.keyInventory).tasp$key());
        if (t == 5 && inv) { double[] q = slot(mc, 36); moveTo(mc, t, q[0], q[1], 10); }        // to hotbar slot 0 (stone)
        if (t == 20) mouse(mc, 0, 1);
        if (t == 21) mouse(mc, 0, 0);                                                           // pick up the stack
        if (t == 24 && inv) { double[] q = slot(mc, 10); moveTo(mc, t, q[0], q[1], 12); }       // carry it across
        if (t == 40) mouse(mc, 1, 1);
        if (t == 41) mouse(mc, 1, 0);                                                           // right click: drop one
        if (t == 44 && inv) { double[] q = slot(mc, 12); moveTo(mc, t, q[0], q[1], 8); }
        if (t == 56) mouse(mc, 0, 1);
        if (t == 57) mouse(mc, 0, 0);                                                           // place the rest
        if (t == 60 && inv) { double[] q = slot(mc, 37); moveTo(mc, t, q[0], q[1], 10); }       // hover planks: tooltip
        if (t == 95 && inv) {                                                                   // the recipe book button
            var a = (dev.dbenton.tasplayback.fabric.mc12111.mixin.ContainerScreenAccessor) mc.screen;
            moveTo(mc, t, a.tasp$left() + 114, mc.screen.height / 2 - 13, 10);
        }
        if (t == 110) mouse(mc, 0, 1);
        if (t == 111) mouse(mc, 0, 0);                                                          // open the recipe book
        if (t == 115 && inv) {                                                                  // its search box
            int x = (mc.screen.width - 147) / 2 - 86, y = (mc.screen.height - 166) / 2;
            moveTo(mc, t, x + 25 + 40, y + 13 + 7, 10);
        }
        if (t == 130) mouse(mc, 0, 1);
        if (t == 131) mouse(mc, 0, 0);
        if (t >= 135 && t < 140) type(mc, "plank".charAt(t - 135));
        if (t == 150 && inv) { double[] q = slot(mc, 40); moveTo(mc, t, q[0], q[1], 15); }      // wander over the grid
        if (t == 180) { keyEvent(mc, 256, 1); keyEvent(mc, 256, 0); }                           // ESC closes the inventory
        if (t == 190) KeyMapping.click(((dev.dbenton.tasplayback.fabric.mc12111.mixin.KeyMappingAccessor) o.keyChat).tasp$key());
        if (t >= 196 && t < 205) type(mc, "hello tas".charAt(t - 196));
        if (t == 210) { keyEvent(mc, 257, 1); keyEvent(mc, 257, 0); }                           // Enter sends it
        if (t == 230) { keyEvent(mc, 292, 1); keyEvent(mc, 292, 0); }                           // F3 on
        if (t == 260) { keyEvent(mc, 292, 1); keyEvent(mc, 292, 0); }                           // F3 off
        if (t == 280) { keyEvent(mc, 256, 1); keyEvent(mc, 256, 0); }                           // ESC: pause menu
        if (t == 300) { keyEvent(mc, 256, 1); keyEvent(mc, 256, 0); }                           // ESC: back to game
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
