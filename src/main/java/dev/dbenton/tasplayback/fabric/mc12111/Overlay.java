package dev.dbenton.tasplayback.fabric.mc12111;

import dev.dbenton.tasplayback.core.frame.FrameRecord;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Draws on top of the GUI: during replay, the recorded mouse cursor (the OS cursor never appears in the
 * framebuffer) and the replay status line; while recording, a small REC indicator.
 */
final class Overlay {
    private static final Identifier CURSOR = Identifier.fromNamespaceAndPath("tasplayback", "cursor");
    private static final boolean SHOW_CURSOR = !Boolean.getBoolean("tasplayback.noCursor");

    private Overlay() {}

    static void render(Minecraft mc, GuiGraphics g) {
        TaspClient c = TaspClient.get();
        ReplaySession r = c.replay();
        if (r != null) {
            FrameRecord f = r.current;
            if (f != null && SHOW_CURSOR && (f.flags & FrameRecord.FLAG_MOUSE_GRABBED) == 0) {
                double scale = mc.getWindow().getGuiScale();
                // map recorded window coordinates onto the current window (exact when sizes match)
                double sx = f.windowW > 0 ? (double) mc.getWindow().getScreenWidth() / f.windowW : 1;
                double sy = f.windowH > 0 ? (double) mc.getWindow().getScreenHeight() / f.windowH : 1;
                double px = f.mouseX * sx * mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getScreenWidth());
                double py = f.mouseY * sy * mc.getWindow().getHeight() / Math.max(1, mc.getWindow().getScreenHeight());
                g.nextStratum();
                g.pose().pushMatrix();
                g.pose().translate((float) (px / scale), (float) (py / scale));
                g.pose().scale((float) (1.0 / scale), (float) (1.0 / scale));
                g.blitSprite(RenderPipelines.GUI_TEXTURED, CURSOR, 0, 0, 16, 16);
                g.pose().popMatrix();
            }
            if (!Boolean.getBoolean("tasplayback.cleanOutput")) {
                g.nextStratum();
                g.drawString(mc.font, r.status(), 4, mc.getWindow().getGuiScaledHeight() - 12, 0xFFFFFFFF);
            }
        } else if (c.recordingIndicator() && !Boolean.getBoolean("tasplayback.cleanOutput")) {
            g.drawString(mc.font, c.isArmedOnly() ? "REC armed" : "● REC", 4, 4, c.isArmedOnly() ? 0xFFFFAA00 : 0xFFFF3030);
        }
    }
}
