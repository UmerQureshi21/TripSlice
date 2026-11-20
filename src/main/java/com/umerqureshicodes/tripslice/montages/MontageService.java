package com.umerqureshicodes.tripslice.montages;


import com.umerqureshicodes.tripslice.FFmpeg.FFmpegService;
import com.umerqureshicodes.tripslice.ai.AiClient;
import com.umerqureshicodes.tripslice.common.ApiException;
import org.springframework.http.HttpStatus;
import com.umerqureshicodes.tripslice.limits.UsageService;
import com.umerqureshicodes.tripslice.WebSocket.WebSocketServiceMessage;
import com.umerqureshicodes.tripslice.s3.S3Service;
import com.umerqureshicodes.tripslice.users.AppUser;
import com.umerqureshicodes.tripslice.users.UserRepo;
import com.umerqureshicodes.tripslice.videos.*;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.URI;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MontageService {

    private static final Logger log = LoggerFactory.getLogger(MontageService.class);
    private static final int MAX_MONTAGES_PER_USER = 10;
    private static final int MAX_VIDEOS_PER_MONTAGE = 5;
    private static final int MAX_TOPIC_LENGTH = 100;
    private static final int MAX_NAME_LENGTH = 60;
    // The interval list comes from an LLM answer to a user-written topic, so it can't be trusted to be short
    private static final int MAX_INTERVALS_PER_VIDEO = 10;
    private static final int MAX_INTERVALS_TOTAL = 30;

    private final FFmpegService fFmpegService;
    private final MontageRepo montageRepo;
    private final UserRepo userRepo;
    private final VideoService videoService;
    private final VideoRepo videoRepo;
    private final S3Service s3Service;
    private final AiClient aiClient;
    private final SimpMessagingTemplate messagingTemplate;
    private final UsageService usageService;
    // Matches intervals like 00:04-00:08 in the TwelveLabs answer
    private static final Pattern INTERVAL_PATTERN = Pattern.compile("(\\d{1,2}):(\\d{2})\\s*-\\s*(\\d{1,2}):(\\d{2})");

    // State for a single montage request. The service is shared by all requests, so this can't be stored in fields
    private static class MontageBuild {
        int duration = 0;
        final List<Video> videosUsed = new ArrayList<>();
        final List<Path> trimmedFiles = new ArrayList<>();
    }

    public MontageService(MontageRepo montageRepo, VideoService videoService, S3Service s3Service, AiClient aiClient, SimpMessagingTemplate messagingTemplate, FFmpegService fFmpegService, UserRepo userRepo, VideoRepo videoRepo, UsageService usageService) {
        this.usageService = usageService;
        this.montageRepo = montageRepo;
        this.videoService = videoService;
        this.s3Service = s3Service;
        this.aiClient = aiClient;
        this.messagingTemplate = messagingTemplate;
        this.fFmpegService = fFmpegService;
        this.userRepo = userRepo;
        this.videoRepo = videoRepo;
    }

    private void notify(String userEmail, String message, String montagePath) {
        // Only sent to the user who created the montage, they subscribe to /user/queue/montage-progress
        messagingTemplate.convertAndSendToUser(userEmail, "/queue/montage-progress", new WebSocketServiceMessage(message, montagePath));
    }

    public MontageResponseDTO convertToDTO(Montage montage, String montageUrl) {
        List<VideoResponseDTO> videoResponseDTOs = new ArrayList<>();
        for (Video video: montage.getVideos()){
            videoResponseDTOs.add(new VideoResponseDTO(video.getId(), video.getName(),video.getVideoId(),video.isEmbedded()));
        }
        return new MontageResponseDTO(montage.getId(), montage.getName(),videoResponseDTOs,montage.getPrompt(), montage.getCreatedAt(), montage.getDuration(),montageUrl, montage.isEmbedded());
    }

    public MontageResponseDTO createMontage(MontageRequestDTO montageRequestDTO, String email) {

        String name = montageRequestDTO.name() == null ? "" : montageRequestDTO.name().trim();
        String topic = montageRequestDTO.prompt() == null ? "" : montageRequestDTO.prompt().trim();
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            throw new MontageCreationException("The montage name must be 1 to " + MAX_NAME_LENGTH + " characters.");
        }
        if (topic.isEmpty() || topic.length() > MAX_TOPIC_LENGTH) {
            throw new MontageCreationException("The topic must be 1 to " + MAX_TOPIC_LENGTH + " characters.");
        }
        if (montageRequestDTO.videoRequestDTOs() == null || montageRequestDTO.videoRequestDTOs().isEmpty()) {
            throw new MontageCreationException("Select at least one video.");
        }
        // Duplicates removed first, each distinct video costs one TwelveLabs analyze call
        LinkedHashSet<String> videoIds = new LinkedHashSet<>();
        for (VideoRequestDTO v : montageRequestDTO.videoRequestDTOs()) {
            if (v != null && v.getVideoId() != null) {
                videoIds.add(v.getVideoId());
            }
        }
        if (videoIds.isEmpty() || videoIds.size() > MAX_VIDEOS_PER_MONTAGE) {
            throw new MontageCreationException("Select between 1 and " + MAX_VIDEOS_PER_MONTAGE + " videos.");
        }

        if(montageRepo.findAllByUserUsername(email).size() >= MAX_MONTAGES_PER_USER){
            throw new MontageCreationException("You've reached the limit of 10 montages.");
        }

        Optional<AppUser> user = userRepo.findByUsername(email) ;
        if(user.isEmpty()) {
            throw new MontageCreationException("User not found.");
        }

        // Only allow videos that belong to this user
        List<Video> selectedVideos = new ArrayList<>();
        for (String videoId : videoIds) {
            Optional<Video> video = videoRepo.findByVideoIdAndUserUsername(videoId, email);
            if (video.isEmpty()) {
                throw new MontageCreationException("One of the selected videos could not be found.");
            }
            if (video.get().getAssetId() == null) {
                throw new MontageCreationException("\"" + video.get().getName()
                        + "\" is still being processed. Please try again in a minute.");
            }
            selectedVideos.add(video.get());
        }

        // Daily budgets, so deleting and recreating montages can't run up unlimited TwelveLabs calls
        usageService.consume(user.get().getId(), UsageService.Kind.MONTAGE, 1);
        usageService.consume(user.get().getId(), UsageService.Kind.ANALYZE, selectedVideos.size());

        MontageBuild build = new MontageBuild();
        Map<Video, String> timestamps = analyzeVideoWithPrompt(selectedVideos, topic, email);
        if (timestamps.isEmpty()) {
            throw new MontageCreationException("Couldn't analyze the selected videos right now. Please try again later.");
        }
        // Unique key so montages with the same name (or users with similar emails) never overwrite each other
        String s3Key = "montages/" + user.get().getId() + "/" + UUID.randomUUID() + ".mp4";
        int ffmpegCode = 1;
        try {
            if (trimVideos(timestamps, build, email, topic)) {
                ffmpegCode = combineVideos(build.trimmedFiles, s3Key, email);
            }
        } finally {
            for (Path trimmedFile : build.trimmedFiles) {
                deleteQuietly(trimmedFile);
            }
        }

        if(ffmpegCode != 0) {
            log.warn("Montage build failed with exit code {}", ffmpegCode);
            throw new MontageCreationException("Something went wrong while creating the montage. Please try again.");
        }

        Montage montage = new Montage(name, topic, user.get(), build.duration);
        montage.setS3Key(s3Key);
        // Only the videos that clips were actually taken from, this also adds the montage to each video
        montage.setVideos(build.videosUsed);
        Montage savedMontage = montageRepo.save(montage);

        // Make the montage searchable. If this fails the background job retries it
        if (aiClient.indexDocument(user.get().getId(), AiClient.KIND_MONTAGE, savedMontage.getId(),
                buildMontageText(savedMontage))) {
            savedMontage.setEmbedded(true);
            savedMontage = montageRepo.save(savedMontage);
        }

        String preSignedUrl = s3Service.generatePresignedGetUrl(s3Key).toString();
        notify(email, name + " created!", preSignedUrl);
        return convertToDTO(savedMontage, preSignedUrl);
    }

    //https://docs.twelvelabs.io/v1.3/api-reference/analyze-videos/analyze
    // Returns the raw interval text for each video that TwelveLabs answered for
    public Map<Video, String> analyzeVideoWithPrompt(List<Video> videos, String topic, String email) {
        Map<Video, String> timestamps = new LinkedHashMap<>();
        for(Video video : videos) {
            // The AI service owns the prompt, this passes the topic the user typed
            String intervals = aiClient.findIntervals(video.getAssetId(), topic);
            if (intervals != null) {
                timestamps.put(video, intervals);
                notify(email, "Successfully extracted " + topic + " from " + video.getName(), null);
            }
        }
        return timestamps;
    }

    // Trims every interval into its own temp file (added to build.trimmedFiles), returns false if something failed
    // and throws if none of the videos contain the topic
    private boolean trimVideos(Map<Video, String> timestamps, MontageBuild build, String userEmail, String topic) {
        int totalIntervals = 0;
        for (Map.Entry<Video, String> entry : timestamps.entrySet()) {
            Video video = entry.getKey();
            List<int[]> parsed = parseIntervals(entry.getValue());
            if (parsed.isEmpty()) {
                continue; // a video with no instance of the topic comes back as 00:00-00:00
            }

            File inputTempFile = null;
            try {
                // Download each source video once, not once per interval
                inputTempFile = Paths.get(System.getProperty("java.io.tmpdir"), "s3-video-" + UUID.randomUUID() + ".mp4").toFile();
                s3Service.downloadToFile(videoService.getS3Name(video), inputTempFile.toPath());
                // Intervals past the end of the video would make empty or failing clips
                double videoSeconds = fFmpegService.probe(inputTempFile).durationSeconds();
                List<int[]> intervals = clampAndMerge(parsed, videoSeconds);
                if (totalIntervals + intervals.size() > MAX_INTERVALS_TOTAL) {
                    intervals = intervals.subList(0, Math.max(0, MAX_INTERVALS_TOTAL - totalIntervals));
                }
                if (intervals.isEmpty()) {
                    continue;
                }
                totalIntervals += intervals.size();
                for (int[] interval : intervals) {
                    Path tempPath = Paths.get(System.getProperty("java.io.tmpdir"), "trimmed-" + UUID.randomUUID() + ".mp4");
                    build.trimmedFiles.add(tempPath);
                    int exitCode = fFmpegService.trimVideo(inputTempFile.getAbsolutePath(), tempPath.toString(),
                            String.valueOf(interval[0]), String.valueOf(interval[1]));
                    if (exitCode != 0) {
                        return false;
                    }
                    build.duration += interval[1] - interval[0];
                }
                build.videosUsed.add(video);
            } catch (FFmpegService.EncoderBusyException e) {
                throw e;
            } catch (Exception e) {
                log.warn("Trimming failed: {}", e.getMessage());
                return false;
            } finally {
                if (inputTempFile != null) {
                    deleteQuietly(inputTempFile.toPath());
                }
            }
        }

        if (build.trimmedFiles.isEmpty()) {
            throw new MontageCreationException("No moments matching \"" + topic + "\" were found in the selected videos.");
        }
        notify(userEmail, "Finished trimming videos...", null);
        return true;
    }

    public int combineVideos(List<Path> trimmedFiles, String s3Key, String userEmail) {
        notify(userEmail, "Combining videos...", null);
        int exitCode = 100;
        File concatFile = null;
        Path tempPath = Paths.get(System.getProperty("java.io.tmpdir"), "montage-" + UUID.randomUUID() + ".mp4");
        try {
            concatFile = Files.createTempFile("videos-", ".txt").toFile();
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(concatFile))) {
                for (Path trimmedFile : trimmedFiles) {
                    writer.write("file '" + trimmedFile.toString() + "'\n");
                }
            }
            exitCode = fFmpegService.combineVideo(concatFile.getAbsolutePath(),tempPath.toString());
            if (exitCode == 0) {
                s3Service.putObject(s3Key, tempPath.toFile());
            }
        } catch (FFmpegService.EncoderBusyException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Combining failed: {}", e.getMessage());
            exitCode = 100;
        } finally {
            if (concatFile != null) {
                deleteQuietly(concatFile.toPath());
            }
            deleteQuietly(tempPath);
        }
        return exitCode;
    }

    // Reads "00:04-00:08, 01:02-01:09" style text, at most MAX_INTERVALS_PER_VIDEO entries
    static List<int[]> parseIntervals(String text) {
        List<int[]> intervals = new ArrayList<>();
        Matcher matcher = INTERVAL_PATTERN.matcher(text);
        while (matcher.find() && intervals.size() < MAX_INTERVALS_PER_VIDEO * 4) {
            int start = Integer.parseInt(matcher.group(1)) * 60 + Integer.parseInt(matcher.group(2));
            int end = Integer.parseInt(matcher.group(3)) * 60 + Integer.parseInt(matcher.group(4));
            if (end > start) {
                intervals.add(new int[]{start, end});
            }
        }
        return intervals;
    }

    // Keeps intervals inside the video, sorts them and joins overlapping ones, so the same moment isn't cut twice
    static List<int[]> clampAndMerge(List<int[]> intervals, double videoSeconds) {
        int limit = (int) Math.floor(videoSeconds);
        List<int[]> clamped = new ArrayList<>();
        for (int[] interval : intervals) {
            int start = Math.max(0, interval[0]);
            int end = Math.min(limit, interval[1]);
            if (end > start) {
                clamped.add(new int[]{start, end});
            }
        }
        clamped.sort(Comparator.comparingInt(i -> i[0]));
        List<int[]> merged = new ArrayList<>();
        for (int[] interval : clamped) {
            if (!merged.isEmpty() && interval[0] <= merged.getLast()[1]) {
                merged.getLast()[1] = Math.max(merged.getLast()[1], interval[1]);
            } else {
                merged.add(interval);
            }
        }
        return merged.size() > MAX_INTERVALS_PER_VIDEO ? new ArrayList<>(merged.subList(0, MAX_INTERVALS_PER_VIDEO)) : merged;
    }

    // What gets embedded for a montage: the user's own words plus whatever the source videos show
    public String buildMontageText(Montage montage) {
        StringBuilder text = new StringBuilder(montage.getName() + ". " + montage.getPrompt() + ".");
        for (Video video : montage.getVideos()) {
            if (video.getSummary() != null) {
                text.append(" ").append(video.getSummary());
            }
        }
        return text.toString();
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Failed to delete temp file {}: {}", path.getFileName(), e.getMessage());
        }
    }

    public List<MontageResponseDTO> getMontages(String userEmail) {
        List<MontageResponseDTO> montageResponseDTOs = new ArrayList<>();
        List<Montage> montages = montageRepo.findAllByUserUsername(userEmail) ;
        for (Montage montage : montages) {
            List<VideoResponseDTO> videoResponseDTOs = new ArrayList<>();
            List<Video> videos = montage.getVideos();
            for (Video video : videos) {
                String videoPreviewUrl = videoService.getVideoUrl(videoService.getS3Name(video));
                videoResponseDTOs.add(new VideoResponseDTO(video.getId(), video.getName(),video.getVideoId(),videoPreviewUrl,video.isEmbedded()));
            }
            String preSignedUrl = s3Service.generatePresignedGetUrl(getS3Key(montage, userEmail)).toString();
            montageResponseDTOs.add(new MontageResponseDTO(montage.getId(), montage.getName(),videoResponseDTOs,montage.getPrompt(),montage.getCreatedAt(),montage.getDuration(),preSignedUrl,montage.isEmbedded()));
        }
        return montageResponseDTOs;
    }

    public String getS3Key(Montage montage, String userEmail) {
        if (montage.getS3Key() != null) {
            return montage.getS3Key();
        }
        // Montages created before s3Key was stored used this path
        return "montages/" + userEmail.split("@")[0] + "/" + montage.getName() + ".mp4";
    }

    @Transactional
    public void deleteMontage(Long montageId, String userEmail) {
        // Only finds the montage if it belongs to this user, so users can't delete each other's montages
        Optional<Montage> montageOptional = montageRepo.findByIdAndUserUsername(montageId, userEmail);
        if (montageOptional.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "That montage couldn't be found. It may already be deleted.");
        }
        Montage montage = montageOptional.get();
        montageRepo.delete(montage);
        aiClient.deleteDocument(AiClient.KIND_MONTAGE, montage.getId());

        // Best effort cleanup, a failure here shouldn't undo the db delete
        try {
            s3Service.deleteObject(getS3Key(montage, userEmail));
        } catch (Exception e) {
            log.warn("Failed to delete montage from S3: {}", e.getMessage());
        }
    }

    public MontageResponseDTO getLongestMontage(String userEmail) {
        List<MontageResponseDTO> montages = this.getMontages(userEmail);
        if(montages.isEmpty()) {
            return null;
        }
        int max = montages.getFirst().duration();
        int maxIndex = 0;
        for (int i = 1; i < montages.size(); i++) {
            if (montages.get(i).duration() > max) {
                max = montages.get(i).duration();
                maxIndex = i;
            }
        }
        return montages.get(maxIndex);
    }

    public MontageResponseDTO getMostVideoMontage(String userEmail) {
        List<MontageResponseDTO> montages = this.getMontages(userEmail);
        if(montages.isEmpty()) {
            return null;
        }
        int max = montages.getFirst().videos().size();
        int maxIndex = 0;
        for (int i = 1; i < montages.size(); i++) {
            int count = montages.get(i).videos().size();
            if (count > max) {
                max = count ;
                maxIndex = i;
            }
        }
        return montages.get(maxIndex);
    }
}
