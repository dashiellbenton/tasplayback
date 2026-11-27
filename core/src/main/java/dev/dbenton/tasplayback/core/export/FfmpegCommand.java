package dev.dbenton.tasplayback.core.export;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Builds the ffmpeg argument list for the video exporter (PLAN.md §6.5). Raw RGBA frames are piped
 * into ffmpeg's stdin; the offline-mixed WAV is muxed in as a second input.
 *
 * <p>The result is an argument vector for {@link ProcessBuilder}, never a shell string, so paths with
 * spaces or quotes need no escaping.
 */
public final class FfmpegCommand {
    public enum Mode { VIDEO, IMAGE_SEQUENCE }

    private String ffmpeg = "ffmpeg";
    private int width, height;
    private int fpsNum = 60, fpsDen = 1;
    private String inputPixelFormat = "rgba";
    private boolean flipVertical = true;
    private Mode mode = Mode.VIDEO;
    private String videoCodec = "libx264";
    private Integer crf = 18;
    private String preset = "medium";
    private String outputPixelFormat = "yuv420p";
    private String audioPath;
    private String audioCodec = "aac";
    private String audioBitrate = "320k";
    private String output;
    private boolean overwrite = true;
    private final List<String> extraOutputArgs = new ArrayList<>();

    public FfmpegCommand ffmpeg(String path) { this.ffmpeg = req(path, "ffmpeg path"); return this; }
    public FfmpegCommand size(int w, int h) { this.width = w; this.height = h; return this; }
    public FfmpegCommand fps(int num, int den) { this.fpsNum = num; this.fpsDen = den; return this; }
    public FfmpegCommand fps(int fps) { return fps(fps, 1); }
    public FfmpegCommand inputPixelFormat(String f) { this.inputPixelFormat = req(f, "pixel format"); return this; }
    /** OpenGL read-back is bottom-up; flip by default. */
    public FfmpegCommand flipVertical(boolean flip) { this.flipVertical = flip; return this; }
    public FfmpegCommand mode(Mode m) { this.mode = m; return this; }
    public FfmpegCommand videoCodec(String c) { this.videoCodec = req(c, "codec"); return this; }
    /** Null disables the -crf argument (e.g. for codecs that don't support it). */
    public FfmpegCommand crf(Integer crf) { this.crf = crf; return this; }
    /** Null disables the -preset argument. */
    public FfmpegCommand preset(String p) { this.preset = p; return this; }
    /** Null keeps the encoder's default output pixel format. */
    public FfmpegCommand outputPixelFormat(String f) { this.outputPixelFormat = f; return this; }
    public FfmpegCommand audio(String wavPath) { this.audioPath = wavPath; return this; }
    public FfmpegCommand audioCodec(String codec, String bitrate) { this.audioCodec = req(codec, "audio codec"); this.audioBitrate = bitrate; return this; }
    /** Output file, or for IMAGE_SEQUENCE a printf pattern such as {@code frames/%06d.png}. */
    public FfmpegCommand output(String path) { this.output = req(path, "output"); return this; }
    public FfmpegCommand overwrite(boolean o) { this.overwrite = o; return this; }
    public FfmpegCommand extraOutputArg(String arg) { extraOutputArgs.add(req(arg, "argument")); return this; }

    private static String req(String v, String what) {
        if (v == null || v.isEmpty()) throw new IllegalArgumentException(what + " must not be empty");
        return v;
    }

    public List<String> build() {
        if (width <= 0 || height <= 0) throw new IllegalStateException("size must be positive");
        if (fpsNum <= 0 || fpsDen <= 0) throw new IllegalStateException("fps must be positive");
        if (output == null) throw new IllegalStateException("output not set");
        if (mode == Mode.VIDEO && "yuv420p".equals(outputPixelFormat) && (width % 2 != 0 || height % 2 != 0)) {
            throw new IllegalStateException("yuv420p needs even width and height, got " + width + "x" + height);
        }
        if (mode == Mode.IMAGE_SEQUENCE && !output.contains("%")) {
            throw new IllegalStateException("image sequence output needs a frame-number pattern like %06d");
        }
        List<String> a = new ArrayList<>();
        a.add(ffmpeg);
        a.add("-hide_banner");
        a.add("-loglevel"); a.add("error");
        a.add(overwrite ? "-y" : "-n");
        // input 0: raw frames on stdin
        a.add("-f"); a.add("rawvideo");
        a.add("-pix_fmt"); a.add(inputPixelFormat);
        a.add("-s"); a.add(width + "x" + height);
        a.add("-framerate"); a.add(fpsDen == 1 ? Integer.toString(fpsNum) : fpsNum + "/" + fpsDen);
        a.add("-i"); a.add("-");
        boolean withAudio = audioPath != null && mode == Mode.VIDEO;
        if (withAudio) { a.add("-i"); a.add(audioPath); }
        if (flipVertical) { a.add("-vf"); a.add("vflip"); }
        if (mode == Mode.VIDEO) {
            a.add("-map"); a.add("0:v:0");
            if (withAudio) { a.add("-map"); a.add("1:a:0"); }
            a.add("-c:v"); a.add(videoCodec);
            if (crf != null) { a.add("-crf"); a.add(Integer.toString(crf)); }
            if (preset != null) { a.add("-preset"); a.add(preset); }
            if (outputPixelFormat != null) { a.add("-pix_fmt"); a.add(outputPixelFormat); }
            if (withAudio) {
                a.add("-c:a"); a.add(audioCodec);
                if (audioBitrate != null) { a.add("-b:a"); a.add(audioBitrate); }
            }
        } else {
            a.add("-start_number"); a.add("0");
        }
        a.addAll(extraOutputArgs);
        a.add(output);
        return Collections.unmodifiableList(a);
    }

    /** Frame duration in seconds for this command's frame rate. */
    public double frameSeconds() { return (double) fpsDen / fpsNum; }

    /** Human-readable rendering for logs only (do not execute through a shell). */
    public String describe() {
        StringBuilder b = new StringBuilder();
        for (String s : build()) {
            if (b.length() > 0) b.append(' ');
            b.append(s.matches("[A-Za-z0-9_./:%=+-]+") ? s : "'" + s.replace("'", "'\\''") + "'");
        }
        return String.format(Locale.ROOT, "%s", b);
    }
}
