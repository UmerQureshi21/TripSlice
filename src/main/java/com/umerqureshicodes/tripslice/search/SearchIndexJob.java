package com.umerqureshicodes.tripslice.search;

import com.umerqureshicodes.tripslice.ai.AiClient;
import com.umerqureshicodes.tripslice.montages.Montage;
import com.umerqureshicodes.tripslice.montages.MontageRepo;
import com.umerqureshicodes.tripslice.montages.MontageService;
import com.umerqureshicodes.tripslice.videos.Video;
import com.umerqureshicodes.tripslice.videos.VideoRepo;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

// Summaries can't be made at upload time because TwelveLabs is still indexing the video, and the call
// would fail. This job keeps trying in the background, which also covers videos uploaded before search existed.
@Component
public class SearchIndexJob {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexJob.class);
    private static final int MAX_ATTEMPTS = 5;

    private final VideoRepo videoRepo;
    private final MontageRepo montageRepo;
    private final MontageService montageService;
    private final AiClient aiClient;

    public SearchIndexJob(VideoRepo videoRepo, MontageRepo montageRepo, MontageService montageService,
                          AiClient aiClient) {
        this.videoRepo = videoRepo;
        this.montageRepo = montageRepo;
        this.montageService = montageService;
        this.aiClient = aiClient;
    }

    // A few at a time so a backlog doesn't turn into a burst of API calls
    @Scheduled(initialDelayString = "${embeddings.job.initial-delay:30000}",
               fixedDelayString = "${embeddings.job.interval:60000}")
    @Transactional
    public void run() {
        summarizeVideos();
        embedMontages();
    }

    private void summarizeVideos() {
        List<Video> videos = videoRepo.findTop3ByEmbeddedFalseAndSummaryAttemptsLessThan(MAX_ATTEMPTS);
        for (Video video : videos) {
            String summary = video.getSummary();

            // A video that already has a summary only needs embedding, no TwelveLabs analysis
            if (summary == null) {
                // Counted before the call so a video that always fails eventually stops being retried
                video.setSummaryAttempts(video.getSummaryAttempts() + 1);

                // Still indexing, or no longer in the index at all
                if (video.getAssetId() == null) {
                    video.setAssetId(aiClient.getAssetId(video.getVideoId()));
                    if (video.getAssetId() == null) {
                        log.info("No asset id yet for video {}, attempt {}", video.getId(), video.getSummaryAttempts());
                        continue;
                    }
                }

                summary = aiClient.summarize(video.getAssetId());
                if (summary == null) {
                    log.info("No summary yet for video {}, attempt {}", video.getId(), video.getSummaryAttempts());
                    continue;
                }
                video.setSummary(summary);
            }

            if (aiClient.indexDocument(video.getUser().getId(), AiClient.KIND_VIDEO, video.getId(), summary)) {
                video.setEmbedded(true);
                log.info("Video {} is now searchable", video.getId());
            }
        }
    }

    private void embedMontages() {
        for (Montage montage : montageRepo.findTop3ByEmbeddedFalse()) {
            if (aiClient.indexDocument(montage.getUser().getId(), AiClient.KIND_MONTAGE, montage.getId(),
                    montageService.buildMontageText(montage))) {
                montage.setEmbedded(true);
                log.info("Embedded montage {}", montage.getId());
            }
        }
    }
}
