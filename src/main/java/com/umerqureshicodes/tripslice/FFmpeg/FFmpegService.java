package com.umerqureshicodes.tripslice.FFmpeg;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Every FFmpeg and ffprobe call. Uploads are untrusted, so:
 * - inputs may only be read from local files (no http, no playlists pulling in other files)
 * - only real video containers are opened, HLS playlists, concat lists, images etc. are refused
 * - every process has a time limit, and only a few encodes run at once so uploads can't starve the server
 */
@Service
public class FFmpegService {

    private static final Logger log = LoggerFactory.getLogger(FFmpegService.class);

    // Demuxers allowed for user files. "mov" covers mp4/mov/m4v/3gp, "matroska" covers mkv/webm
    private static final String FORMAT_WHITELIST = "mov,matroska,avi";
    private static final String PROTOCOL_WHITELIST = "file";
    // Waiting this long for a free encoder means the server is overloaded, so the request is refused
    private static final long QUEUE_WAIT_SECONDS = 120;
    private static final long PROBE_TIMEOUT_SECONDS = 30;

    public record ProbeResult(double durationSeconds, int width, int height) {}

    private final Semaphore encoders;
    private final long timeoutSeconds;

    public FFmpegService(@Value("${ffmpeg.max-concurrent:2}") int maxConcurrent,
                         @Value("${ffmpeg.timeout-seconds:300}") long timeoutSeconds) {
        this.encoders = new Semaphore(maxConcurrent, true);
        this.timeoutSeconds = timeoutSeconds;
    }

    /** Reads duration and size without decoding. Throws if the file isn't an allowed video. */
    public ProbeResult probe(File file) throws IOException, InterruptedException {
        List<String> command = List.of(
                "ffprobe", "-v", "error",
                "-protocol_whitelist", PROTOCOL_WHITELIST,
                "-format_whitelist", FORMAT_WHITELIST,
                "-select_streams", "v:0",
                "-show_entries", "stream=width,height:format=duration",
                "-of", "default=noprint_wrappers=1",
                file.getAbsolutePath()
        );
        File output = Files.createTempFile("ffprobe-", ".txt").toFile();
        try {
            int exitCode = run(command, output, PROBE_TIMEOUT_SECONDS);
            if (exitCode != 0) {
                throw new InvalidVideoException("Unsupported or corrupt video file");
            }
            Map<String, String> values = new HashMap<>();
            for (String line : Files.readAllLines(output.toPath(), StandardCharsets.UTF_8)) {
                int eq = line.indexOf('=');
                if (eq > 0) {
                    values.putIfAbsent(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
                }
            }
            if (!values.containsKey("width") || !values.containsKey("height")) {
                throw new InvalidVideoException("The file has no video stream");
            }
            try {
                return new ProbeResult(
                        Double.parseDouble(values.get("duration")),
                        Integer.parseInt(values.get("width")),
                        Integer.parseInt(values.get("height")));
            } catch (NumberFormatException | NullPointerException e) {
                throw new InvalidVideoException("Could not read the video's duration");
            }
        } finally {
            Files.deleteIfExists(output.toPath());
        }
    }

    public int convertToMp4(String inputFile, String outputFile) throws IOException, InterruptedException {
        return encode(List.of(
                "ffmpeg", "-nostdin", "-y",
                "-protocol_whitelist", PROTOCOL_WHITELIST,
                "-format_whitelist", FORMAT_WHITELIST,
                "-i", inputFile,
                "-c:v", "libx264", "-preset", "fast",
                "-c:a", "aac",
                outputFile
        ));
    }

    public int trimVideo(String inputFile, String outputFile, String start, String end) throws IOException, InterruptedException {
        return encode(List.of(
                "ffmpeg", "-nostdin", "-y",
                "-protocol_whitelist", PROTOCOL_WHITELIST,
                "-format_whitelist", FORMAT_WHITELIST,
                "-i", inputFile,
                "-ss", start,
                "-to", end,
                "-c:v", "libx264", "-crf", "23", "-preset", "fast",
                "-c:a", "aac", "-b:a", "192k",
                outputFile
        ));
    }

    /** The concat list is written by the server and only names its own temp files. */
    public int combineVideo(String concatFilePath, String outputFile) throws IOException, InterruptedException {
        return encode(List.of(
                "ffmpeg", "-nostdin", "-y",
                "-protocol_whitelist", PROTOCOL_WHITELIST,
                "-f", "concat", "-safe", "0",
                "-i", concatFilePath,
                "-c:v", "libx264", "-crf", "23", "-preset", "veryfast",
                "-c:a", "aac",
                outputFile
        ));
    }

    private int encode(List<String> command) throws IOException, InterruptedException {
        if (!encoders.tryAcquire(QUEUE_WAIT_SECONDS, TimeUnit.SECONDS)) {
            throw new EncoderBusyException();
        }
        File output = Files.createTempFile("ffmpeg-", ".log").toFile();
        try {
            int exitCode = run(command, output, timeoutSeconds);
            if (exitCode != 0) {
                log.warn("ffmpeg exited with {}: {}", exitCode, tail(output, 20));
            }
            return exitCode;
        } finally {
            encoders.release();
            Files.deleteIfExists(output.toPath());
        }
    }

    // Output goes to a file rather than a pipe, so waiting with a timeout can't deadlock on a full buffer
    private int run(List<String> command, File output, long timeout) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(new ArrayList<>(command));
        pb.redirectErrorStream(true);
        pb.redirectOutput(output);
        Process process = pb.start();
        if (!process.waitFor(timeout, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
            log.warn("{} timed out after {}s", command.get(0), timeout);
            return -1;
        }
        return process.exitValue();
    }

    private String tail(File file, int lines) {
        try {
            List<String> all = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            return String.join("\n", all.subList(Math.max(0, all.size() - lines), all.size()));
        } catch (IOException e) {
            return "(no output)";
        }
    }

    public static class InvalidVideoException extends RuntimeException {
        public InvalidVideoException(String message) { super(message); }
    }

    public static class EncoderBusyException extends RuntimeException {
        public EncoderBusyException() { super("The server is busy processing other videos. Please try again in a few minutes."); }
    }
}
