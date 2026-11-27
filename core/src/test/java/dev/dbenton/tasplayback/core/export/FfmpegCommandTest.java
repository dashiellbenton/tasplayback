package dev.dbenton.tasplayback.core.export;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FfmpegCommandTest {
    @Test
    void videoWithAudio() {
        List<String> a = new FfmpegCommand().size(1920, 1080).fps(60).audio("/tmp/my run/audio.wav").output("/tmp/out dir/run.mp4").build();
        assertEquals(Arrays.asList("ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
                "-f", "rawvideo", "-pix_fmt", "rgba", "-s", "1920x1080", "-framerate", "60", "-i", "-",
                "-i", "/tmp/my run/audio.wav", "-vf", "vflip",
                "-map", "0:v:0", "-map", "1:a:0", "-c:v", "libx264", "-crf", "18", "-preset", "medium", "-pix_fmt", "yuv420p",
                "-c:a", "aac", "-b:a", "320k", "/tmp/out dir/run.mp4"), a);
    }

    @Test
    void customCodecRationalFpsNoAudio() {
        List<String> a = new FfmpegCommand().ffmpeg("/opt/ffmpeg").size(854, 480).fps(60000, 1001).flipVertical(false)
                .videoCodec("libx265").crf(null).preset("slow").outputPixelFormat("yuv444p").extraOutputArg("-tag:v").extraOutputArg("hvc1")
                .output("a.mkv").overwrite(false).build();
        assertEquals("/opt/ffmpeg", a.get(0));
        assertTrue(a.contains("-n"));
        assertEquals("60000/1001", a.get(a.indexOf("-framerate") + 1));
        assertFalse(a.contains("-crf"));
        assertFalse(a.contains("vflip"));
        assertFalse(a.contains("-map") && a.contains("1:a:0"));
        assertEquals(Arrays.asList("-tag:v", "hvc1", "a.mkv"), a.subList(a.size() - 3, a.size()));
        assertEquals(1001 / 60000.0, new FfmpegCommand().fps(60000, 1001).frameSeconds(), 1e-15);
    }

    @Test
    void imageSequence() {
        List<String> a = new FfmpegCommand().size(855, 481).mode(FfmpegCommand.Mode.IMAGE_SEQUENCE).audio("x.wav")
                .output("frames/%06d.png").build();
        assertFalse(a.contains("x.wav"), "image sequences carry no audio");
        assertFalse(a.contains("-c:v"));
        assertEquals("frames/%06d.png", a.get(a.size() - 1));
    }

    @Test
    void validation() {
        assertThrows(IllegalStateException.class, () -> new FfmpegCommand().size(855, 480).output("a.mp4").build());
        assertThrows(IllegalStateException.class, () -> new FfmpegCommand().output("a.mp4").build());
        assertThrows(IllegalStateException.class, () -> new FfmpegCommand().size(2, 2).build());
        assertThrows(IllegalStateException.class, () -> new FfmpegCommand().size(2, 2).mode(FfmpegCommand.Mode.IMAGE_SEQUENCE).output("a.png").build());
        assertThrows(IllegalArgumentException.class, () -> new FfmpegCommand().output(""));
        assertTrue(new FfmpegCommand().size(2, 2).output("my file.mp4").describe().contains("'my file.mp4'"));
    }
}
