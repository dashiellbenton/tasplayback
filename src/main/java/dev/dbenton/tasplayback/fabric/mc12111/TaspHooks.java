package dev.dbenton.tasplayback.fabric.mc12111;

import dev.dbenton.tasplayback.core.frame.FrameRecord;
import dev.dbenton.tasplayback.fabric.mc12111.mixin.CameraInvoker;
import dev.dbenton.tasplayback.fabric.mc12111.mixin.MouseHandlerAccessor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Small static entry points for mixins that need package-private state. */
public final class TaspHooks {
    private TaspHooks() {}

    public static void overlay(GuiGraphics g) {
        Overlay.render(Minecraft.getInstance(), g);
    }

    public static void overrideCamera(Camera camera) {
        ReplaySession r = TaspClient.get().replay();
        if (r == null || r.current == null) return;
        FrameRecord f = r.current;
        CameraInvoker inv = (CameraInvoker) camera;
        inv.tasp$setRotation(f.yaw, f.pitch);
        inv.tasp$setPosition(f.eyeX, f.eyeY, f.eyeZ);
    }

    /** Wall-clock milliseconds for UI animations: the recorded frame's capture time during replay. */
    public static long uiMillis(long vanilla) {
        ReplaySession r = TaspClient.get().replay();
        if (r == null || r.current == null || r.current.wallNanos == 0) return vanilla;
        return r.current.wallNanos / 1_000_000L;
    }

    static void applyMouse(Minecraft mc, FrameRecord f) {
        MouseHandlerAccessor m = (MouseHandlerAccessor) mc.mouseHandler;
        double sx = f.windowW > 0 ? (double) mc.getWindow().getScreenWidth() / f.windowW : 1;
        double sy = f.windowH > 0 ? (double) mc.getWindow().getScreenHeight() / f.windowH : 1;
        m.tasp$setX(f.mouseX * sx);
        m.tasp$setY(f.mouseY * sy);
    }
}
