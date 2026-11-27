package dev.dbenton.tasplayback.core.audio;

/** Listener position and orientation in Minecraft world coordinates (x east, y up, z south; yaw/pitch in degrees). */
public final class ListenerPose {
    public final double x, y, z;
    public final float yaw, pitch;

    public ListenerPose(double x, double y, double z, float yaw, float pitch) {
        this.x = x; this.y = y; this.z = z; this.yaw = yaw; this.pitch = pitch;
    }

    public static final ListenerPose ORIGIN = new ListenerPose(0, 0, 0, 0, 0);

    /** Unit forward vector, as Minecraft computes its look vector. */
    public double[] forward() {
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        double cp = Math.cos(pr);
        return new double[] { -Math.sin(yr) * cp, -Math.sin(pr), Math.cos(yr) * cp };
    }

    /** Unit up vector (forward rotated up by 90° pitch). */
    public double[] up() {
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch - 90.0);
        double cp = Math.cos(pr);
        return new double[] { -Math.sin(yr) * cp, -Math.sin(pr), Math.cos(yr) * cp };
    }

    /** Listener's right-hand direction, as OpenAL derives it: forward × up. */
    public double[] right() {
        double[] f = forward(), u = up();
        return new double[] { f[1] * u[2] - f[2] * u[1], f[2] * u[0] - f[0] * u[2], f[0] * u[1] - f[1] * u[0] };
    }
}
