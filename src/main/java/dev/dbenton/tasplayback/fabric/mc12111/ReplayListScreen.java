package dev.dbenton.tasplayback.fabric.mc12111;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Lists recordings in {@code .minecraft/tasplayback/recordings} and starts one. */
public final class ReplayListScreen extends Screen {
    private final Screen parent;

    public ReplayListScreen(Screen parent) {
        super(Component.literal("TAS Replays"));
        this.parent = parent;
    }

    @Override protected void init() {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> s = Files.list(TaspClient.get().recordingsDir())) {
            s.filter(p -> p.toString().endsWith(".tasp")).forEach(files::add);
        } catch (IOException ignored) {
        }
        files.sort(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed());
        int y = 32;
        for (Path p : files.subList(0, Math.min(files.size(), (height - 64) / 24))) {
            addRenderableWidget(Button.builder(Component.literal(p.getFileName().toString()),
                    b -> TaspClient.get().startReplay(p, ReplaySession.Mode.REALTIME)).bounds(width / 2 - 150, y, 300, 20).build());
            y += 24;
        }
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose()).bounds(width / 2 - 50, height - 28, 100, 20).build());
    }

    @Override public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        g.drawCenteredString(font, title, width / 2, 12, 0xFFFFFFFF);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
}
