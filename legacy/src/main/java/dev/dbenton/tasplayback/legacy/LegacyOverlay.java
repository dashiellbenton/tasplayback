package dev.dbenton.tasplayback.legacy;

import dev.dbenton.tasplayback.core.frame.FrameRecord;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

/** Replay cursor and status line, recording indicator (1.12.2, fixed-function GL). */
final class LegacyOverlay {
    private static final ResourceLocation CURSOR = new ResourceLocation("tasplayback", "textures/gui/cursor.png");
    private static final boolean SHOW_CURSOR = !Boolean.getBoolean("tasplayback.noCursor");
    private static final boolean CLEAN = Boolean.getBoolean("tasplayback.cleanOutput");

    private LegacyOverlay() {}

    static void render(Minecraft mc) {
        LegacyClient c = LegacyClient.get();
        LegacyReplaySession r = c.replay();
        if (r == null && !c.recordingIndicator()) return;
        ScaledResolution sr = new ScaledResolution(mc);
        mc.entityRenderer.setupOverlayRendering();
        GlStateManager.enableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);
        if (r != null) {
            FrameRecord f = r.current;
            if (f != null && SHOW_CURSOR && (f.flags & FrameRecord.FLAG_MOUSE_GRABBED) == 0) {
                double sx = f.windowW > 0 ? (double) mc.displayWidth / f.windowW : 1;
                double sy = f.windowH > 0 ? (double) mc.displayHeight / f.windowH : 1;
                double scale = sr.getScaleFactor();
                GlStateManager.pushMatrix();
                GlStateManager.translate(f.mouseX * sx / scale, f.mouseY * sy / scale, 300);
                GlStateManager.scale(1.0 / scale, 1.0 / scale, 1);
                mc.getTextureManager().bindTexture(CURSOR);
                Gui.drawModalRectWithCustomSizedTexture(0, 0, 0, 0, 16, 16, 16, 16);
                GlStateManager.popMatrix();
            }
            if (!CLEAN) mc.fontRenderer.drawStringWithShadow(r.status(), 4, sr.getScaledHeight() - 12, 0xFFFFFF);
        } else if (!CLEAN) {
            mc.fontRenderer.drawStringWithShadow(c.isArmedOnly() ? "REC armed" : "● REC", 4, 4, c.isArmedOnly() ? 0xFFAA00 : 0xFF3030);
        }
    }
}
