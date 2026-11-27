package dev.dbenton.tasplayback.legacy;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Lists recordings in {@code .minecraft/tasplayback/recordings}. */
public final class LegacyReplayListScreen extends GuiScreen {
    private final GuiScreen parent;
    private final List<Path> files = new ArrayList<>();

    public LegacyReplayListScreen(GuiScreen parent) { this.parent = parent; }

    @Override public void initGui() {
        files.clear();
        try (Stream<Path> s = Files.list(LegacyClient.get().recordingsDir())) {
            s.filter(p -> p.toString().endsWith(".tasp")).forEach(files::add);
        } catch (IOException ignored) {
        }
        files.sort(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed());
        buttonList.clear();
        int y = 32;
        for (int i = 0; i < Math.min(files.size(), (height - 64) / 24); i++) {
            buttonList.add(new GuiButton(i, width / 2 - 150, y, 300, 20, files.get(i).getFileName().toString()));
            y += 24;
        }
        buttonList.add(new GuiButton(1000, width / 2 - 50, height - 28, 100, 20, "Back"));
    }

    @Override protected void actionPerformed(GuiButton b) {
        if (b.id == 1000) mc.displayGuiScreen(parent);
        else if (b.id >= 0 && b.id < files.size()) LegacyClient.get().startReplay(files.get(b.id), LegacyReplaySession.Mode.REALTIME);
    }

    @Override public void drawScreen(int mx, int my, float pt) {
        drawDefaultBackground();
        drawCenteredString(fontRenderer, "TAS Replays", width / 2, 12, 0xFFFFFF);
        super.drawScreen(mx, my, pt);
    }
}
