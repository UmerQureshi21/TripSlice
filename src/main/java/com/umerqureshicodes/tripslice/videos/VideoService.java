package com.umerqureshicodes.tripslice.videos;


import com.umerqureshicodes.tripslice.FFmpeg.FFmpegService;
import com.umerqureshicodes.tripslice.common.ApiException;
import com.umerqureshicodes.tripslice.ai.AiClient;
import com.umerqureshicodes.tripslice.limits.LimitService;
import com.umerqureshicodes.tripslice.montages.Montage;
import com.umerqureshicodes.tripslice.s3.S3Service;
import com.umerqureshicodes.tripslice.users.AppUser;
import com.umerqureshicodes.tripslice.users.UserRepo;
import com.umerqureshicodes.tripslice.WebSocket.WebSocketServiceMessage;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

@Service
public class VideoService {

    private static final Logger log = LoggerFactory.getLogger(VideoService.class);

    private final VideoRepo videoRepo;
    private final S3Service s3Service;
    private final AiClient aiClient;
    private final FFmpegService ffmpegService;
    private final UserRepo userRepo;
    private final LimitService limitService;
    private final SimpMessagingTemplate messagingTemplate;
    @Autowired
    public VideoService(VideoRepo videoRepo, S3Service s3Service, AiClient aiClient, FFmpegService ffmpegService, UserRepo userRepo, LimitService limitService, SimpMessagingTemplate messagingTemplate) {
        this.videoRepo = videoRepo;
        this.s3Service = s3Service;
        this.aiClient = aiClient;
        this.ffmpegService = ffmpegService;
        this.userRepo = userRepo;
        this.limitService = limitService;
        this.messagingTemplate = messagingTemplate;
    }

    private void notify(String userEmail, String videoName, String stage) {
        // Only sent to the user who is uploading, they subscribe to /user/queue/upload-progress
        messagingTemplate.convertAndSendToUser(userEmail, "/queue/upload-progress", new WebSocketServiceMessage(videoName, stage));
    }

    private static final int MAX_FILES_PER_UPLOAD = 5;
    private static final int MAX_VIDEOS_PER_USER = 15;
    private static final double MIN_SECONDS = 3;
    private static final double MAX_SECONDS = 120; // 2 minutes per uploaded clip
    // Bigger than 4K is refused, it would make a single transcode take far too long
    private static final int MAX_DIMENSION = 4096;
    private static final int MAX_NAME_LENGTH = 100;

    public List<VideoResponseDTO> save(List<MultipartFile> files, String userEmail) {
        if (files == null || files.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No files were uploaded.");
        }
        if (files.size() > MAX_FILES_PER_UPLOAD) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You can upload up to 5 videos at a time.");
        }
        if (videoRepo.findAllByUserUsername(userEmail).size() + files.size() > MAX_VIDEOS_PER_USER) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You can have at most 15 videos. Delete some to upload more.");
        }

        // Every temp file made by this request, deleted at the end whatever happens
        List<File> tempFiles = new ArrayList<>();
        try {
            Map<String, File> videos = new LinkedHashMap<>();
            Map<String, Long> durations = new HashMap<>();
            long totalVideosLength = 0;

            for (MultipartFile file : files) {
                String name = displayName(file.getOriginalFilename());
                File original = Files.createTempFile("upload-", ".bin").toFile();
                tempFiles.add(original);
                file.transferTo(original);

                // Checked before any decoding, so a 10 hour or 8K file is refused in milliseconds
                FFmpegService.ProbeResult probe = ffmpegService.probe(original);
                if (probe.durationSeconds() < MIN_SECONDS || probe.durationSeconds() > MAX_SECONDS) {
                    throw new ApiException(HttpStatus.BAD_REQUEST,
                            "\"" + name + "\" is too long or too short. Each video must be between 3 seconds and 2 minutes.");
                }
                if (probe.width() > MAX_DIMENSION || probe.height() > MAX_DIMENSION) {
                    throw new ApiException(HttpStatus.BAD_REQUEST,
                            "\"" + name + "\" is larger than 4K. Please upload a smaller version.");
                }

                File mp4 = original;
                if (!"video/mp4".equalsIgnoreCase(file.getContentType())) {
                    mp4 = Files.createTempFile("converted-", ".mp4").toFile();
                    tempFiles.add(mp4);
                    int exitCode = ffmpegService.convertToMp4(original.getAbsolutePath(), mp4.getAbsolutePath());
                    if (exitCode != 0) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "\"" + name + "\" could not be converted to mp4.");
                    }
                }
                videos.put(name, mp4);
                long millis = Math.round(probe.durationSeconds() * 1000.0);
                durations.put(name, millis);
                totalVideosLength += millis;
            }

            if (limitService.exceededLimit(totalVideosLength)) {
                log.warn("Global video minutes limit reached");
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                        "TripSlice has reached its video processing limit for now. Please check back later.");
            }

            List<VideoResponseDTO> responses = new ArrayList<>();
            for (Map.Entry<String, File> entry : videos.entrySet()) {
                // S3 first, the AI service indexes the video from a presigned url instead of
                // the file being uploaded twice
                notify(userEmail, entry.getKey(), "uploading");
                Video savedVideo = uploadAndIndex(entry.getValue(), entry.getKey(), userEmail);
                if (savedVideo == null) {
                    // Nothing was indexed, so its minutes go back to the allowance
                    limitService.release(durations.get(entry.getKey()));
                    log.warn("Could not save an uploaded video");
                    continue;
                }
                notify(userEmail, entry.getKey(), "indexing");
                notify(userEmail, entry.getKey(), "saved");
                // Described and embedded by the background job, a minute or two later
                responses.add(new VideoResponseDTO(savedVideo.getId(), savedVideo.getName(), savedVideo.getVideoId(), false));
            }
            if (responses.isEmpty()) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Your videos couldn't be processed right now. Please try again later.");
            }
            return responses;
        } catch (IOException e) {
            log.warn("Upload failed: {}", e.getMessage());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Upload failed. Please try again.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Upload was interrupted. Please try again.");
        } finally {
            for (File f : tempFiles) {
                if (f.exists() && !f.delete()) {
                    log.warn("Failed to delete temp file {}", f.getName());
                }
            }
        }
    }

    // The original file name is only ever shown back to its owner, but it is still user input
    private static String displayName(String originalFilename) {
        String name = originalFilename == null ? "video" : originalFilename;
        name = name.replaceAll("[\\p{Cntrl}/\\\\]", "").trim();
        if (name.isEmpty()) {
            name = "video";
        }
        return name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name;
    }

    public Video uploadAndIndex(File file, String filename, String userEmail) {
        Optional<AppUser> user = userRepo.findByUsername(userEmail);
        if (user.isEmpty()) {
            return null;
        }

        // Unique key, so two videos with the same name can't overwrite each other
        String s3Key = "videos/" + user.get().getId() + "/" + UUID.randomUUID() + ".mp4";
        s3Service.putObject(s3Key, file);

        String videoUrl = s3Service.generatePresignedGetUrl(s3Key).toString();
        String videoId = aiClient.indexVideo(videoUrl);
        if (videoId == null) {
            log.warn("TwelveLabs could not index {}", s3Key);
            s3Service.deleteObject(s3Key); // nothing references it
            return null;
        }

        Video video = new Video(videoId, filename, user.get());
        video.setS3Key(s3Key);
        return videoRepo.save(video);
    }

    public List<VideoResponseDTO> getVideos(String userEmail) {
        List<VideoResponseDTO> videoResponseDTOList = new ArrayList<>();
        for (Video video : videoRepo.findAllByUserUsername(userEmail)) {
                String preSignedUrl = s3Service.generatePresignedGetUrl(this.getS3Name(video)).toString();
                videoResponseDTOList.add(new VideoResponseDTO(video.getId(), video.getName(),video.getVideoId(),preSignedUrl,video.isEmbedded()));
        }
        return videoResponseDTOList;
    }

    public List<Video> getVideosByVideoIds(List<String> videoIds) {
        return  videoRepo.findAllByVideoIds(videoIds);
    }

    @Transactional
    public void updateVideo(Video video, Montage montage) {
        video.getMontages().add(montage);
    }

    @Transactional
    public void deleteVideo(long id, String userEmail) {
        // Only finds the video if it belongs to this user, so users can't delete each other's videos
        Optional<Video> videoOptional = videoRepo.findByIdAndUserUsername(id, userEmail);
        if (videoOptional.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "That video couldn't be found. It may already be deleted.");
        }
        Video video = videoOptional.get();

        // Montage owns the join table, so unlink the video from its montages before deleting it
        for (Montage montage : video.getMontages()) {
            montage.getVideos().remove(video);
        }
        videoRepo.delete(video);
        aiClient.deleteDocument(AiClient.KIND_VIDEO, video.getId());

        // Best effort cleanup, a failure here shouldn't undo the db delete
        try {
            s3Service.deleteObject(this.getS3Name(video));
        } catch (Exception e) {
            log.warn("Failed to delete video from S3: {}", e.getMessage());
        }
        aiClient.deleteVideo(video.getVideoId());
    }

    public String getVideoUrl(String key) {
        return s3Service.generatePresignedGetUrl(key).toString();
    }

    public String getS3Name(Video video) {
        if (video.getS3Key() != null) {
            return video.getS3Key();
        }
        // Videos uploaded before s3Key was stored used this path
        return "test/"+video.getName() + "-" + video.getVideoId();
    }
}
