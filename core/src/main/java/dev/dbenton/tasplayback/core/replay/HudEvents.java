package dev.dbenton.tasplayback.core.replay;

import dev.dbenton.tasplayback.core.event.EventRecord;
import dev.dbenton.tasplayback.core.io.ByteSink;
import dev.dbenton.tasplayback.core.io.ByteSource;
import dev.dbenton.tasplayback.core.io.FormatException;
import dev.dbenton.tasplayback.core.time.TimeKey;

/**
 * Event types of the HUD stream. PLAYER_TICK and INVENTORY payloads are version-specific (the local
 * player's render state after a client tick, and changed inventory slots). THUMBNAIL is core-defined and
 * only written in verification runs.
 */
public final class HudEvents {
    private HudEvents() {}

    public static final int PLAYER_TICK = 1;
    public static final int INVENTORY = 2;
    public static final int THUMBNAIL = 3;
    /** A block change the client predicted for its own action (version-specific payload). */
    public static final int BLOCK_PREDICT = 4;

    /** A small RGB thumbnail of the framebuffer, for comparing playback against the recording. */
    public static final class Thumbnail {
        public final long frame;
        public final int width, height;
        public final byte[] rgb;

        public Thumbnail(long frame, int width, int height, byte[] rgb) {
            if (rgb.length != width * height * 3) throw new IllegalArgumentException("rgb size mismatch");
            this.frame = frame; this.width = width; this.height = height; this.rgb = rgb;
        }

        public EventRecord toRecord(TimeKey key) {
            ByteSink s = new ByteSink(rgb.length + 16);
            s.writeVarLong(frame).writeVarInt(width).writeVarInt(height).writeBytes(rgb);
            return new EventRecord(key, THUMBNAIL, s.toByteArray());
        }

        public static Thumbnail from(EventRecord e) {
            if (e.type != THUMBNAIL) throw new FormatException("not a thumbnail");
            ByteSource in = new ByteSource(e.payload);
            long frame = in.readVarLong();
            int w = in.readVarInt(), h = in.readVarInt();
            if (w <= 0 || h <= 0 || w > 4096 || h > 4096 || (long) w * h * 3 != in.remaining()) throw new FormatException("bad thumbnail size");
            return new Thumbnail(frame, w, h, in.readBytes(in.remaining()));
        }

        /** Mean absolute difference per channel, 0 (identical) .. 255. */
        public double meanAbsDiff(Thumbnail o) {
            if (o.width != width || o.height != height) throw new IllegalArgumentException("thumbnail sizes differ");
            long sum = 0;
            for (int i = 0; i < rgb.length; i++) sum += Math.abs((rgb[i] & 0xFF) - (o.rgb[i] & 0xFF));
            return sum / (double) rgb.length;
        }

        /** Box-downscales an RGBA image (rows top to bottom) to a w×h RGB thumbnail. */
        public static Thumbnail fromRgba(long frame, int srcW, int srcH, byte[] rgba, int w, int h) {
            byte[] out = new byte[w * h * 3];
            for (int ty = 0; ty < h; ty++) {
                int y0 = ty * srcH / h, y1 = Math.max(y0 + 1, (ty + 1) * srcH / h);
                for (int tx = 0; tx < w; tx++) {
                    int x0 = tx * srcW / w, x1 = Math.max(x0 + 1, (tx + 1) * srcW / w);
                    long r = 0, g = 0, b = 0, n = 0;
                    for (int y = y0; y < y1; y++) {
                        for (int x = x0; x < x1; x++) {
                            int p = (y * srcW + x) * 4;
                            r += rgba[p] & 0xFF; g += rgba[p + 1] & 0xFF; b += rgba[p + 2] & 0xFF; n++;
                        }
                    }
                    int o = (ty * w + tx) * 3;
                    out[o] = (byte) (r / n); out[o + 1] = (byte) (g / n); out[o + 2] = (byte) (b / n);
                }
            }
            return new Thumbnail(frame, w, h, out);
        }
    }
}
