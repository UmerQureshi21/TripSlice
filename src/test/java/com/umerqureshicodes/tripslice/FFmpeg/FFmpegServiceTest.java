package com.umerqureshicodes.tripslice.FFmpeg;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

// Runs real ffmpeg/ffprobe on generated files, skipped when they aren't installed
class FFmpegServiceTest {

    @TempDir
    static Path dir;
    static File mp4;
    private final FFmpegService service = new FFmpegService(1, 60);

    @BeforeAll
    static void makeSampleVideo() throws Exception {
        mp4 = dir.resolve("sample.mp4").toFile();
        Process p = new ProcessBuilder("ffmpeg", "-v", "error", "-y",
                "-f", "lavfi", "-i", "testsrc=duration=4:size=320x240:rate=10",
                "-c:v", "libx264", mp4.getAbsolutePath())
                .redirectErrorStream(true).start();
        boolean done;
        try {
            done = p.waitFor(60, TimeUnit.SECONDS);
        } catch (Exception e) {
            done = false;
        }
        assumeTrue(done && p.exitValue() == 0, "ffmpeg not available");
    }

    @Test
    void probesARealVideo() throws Exception {
        FFmpegService.ProbeResult result = service.probe(mp4);
        assertThat(result.durationSeconds()).isBetween(3.9, 4.1);
        assertThat(result.width()).isEqualTo(320);
        assertThat(result.height()).isEqualTo(240);
    }

    @Test
    void refusesAnHlsPlaylistThatPointsAtLocalFiles() throws Exception {
        File playlist = dir.resolve("evil.m3u8").toFile();
        Files.writeString(playlist.toPath(), "#EXTM3U\n#EXT-X-TARGETDURATION:4\n#EXTINF:4,\n"
                + mp4.getAbsolutePath() + "\n#EXT-X-ENDLIST\n");
        assertThatThrownBy(() -> service.probe(playlist)).isInstanceOf(FFmpegService.InvalidVideoException.class);
    }

    @Test
    void refusesAFileWithNoVideo() throws Exception {
        File text = dir.resolve("notes.txt").toFile();
        Files.writeString(text.toPath(), "definitely not a video");
        assertThatThrownBy(() -> service.probe(text)).isInstanceOf(FFmpegService.InvalidVideoException.class);
    }

    @Test
    void convertsAndTrims() throws Exception {
        File out = dir.resolve("trimmed.mp4").toFile();
        assertThat(service.trimVideo(mp4.getAbsolutePath(), out.getAbsolutePath(), "1", "3")).isZero();
        assertThat(service.probe(out).durationSeconds()).isBetween(1.5, 2.5);
    }
}
