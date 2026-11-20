package com.umerqureshicodes.tripslice.montages;

import com.umerqureshicodes.tripslice.limits.UsageService;
import com.umerqureshicodes.tripslice.common.ApiException;
import com.umerqureshicodes.tripslice.support.IntegrationTestBase;
import com.umerqureshicodes.tripslice.videos.Video;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MontageIntegrationTest extends IntegrationTestBase {

    @Autowired
    MontageService montageService;
    @Autowired
    UsageService usageService;

    @BeforeEach
    void s3ServesTheSampleVideo() {
        when(s3Client.getObject(any(GetObjectRequest.class), any(Path.class))).thenAnswer(invocation -> {
            Files.copy(sampleMp4.toPath(), invocation.getArgument(1, Path.class));
            return null;
        });
    }

    private String body(String name, String prompt, List<String> videoIds) throws Exception {
        List<Map<String, String>> videos = new ArrayList<>();
        for (String id : videoIds) videos.add(Map.of("name", "x", "videoId", id));
        return JSON.writeValueAsString(Map.of("name", name, "prompt", prompt, "videoRequestDTOs", videos));
    }

    private org.springframework.test.web.servlet.ResultActions create(TestUser user, String json) throws Exception {
        return mvc.perform(as(post("/montages"), user).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void buildsAMontageFromMatchingMoments() throws Exception {
        TestUser user = newUser();
        Video video = saveVideo(user, "mv-" + user.entity().getId(), "asset-1");
        video.setSummary("A sunny beach.");
        videoRepo.save(video);
        // Overlapping and out of range intervals are cleaned up before trimming
        when(aiClient.findIntervals("asset-1", "beach")).thenReturn("00:01-00:03, 00:02-00:04, 00:30-00:40");
        when(aiClient.indexDocument(anyLong(), eq("MONTAGE"), anyLong(), anyString())).thenReturn(true);

        // The same video listed twice is analyzed once
        create(user, body("Beach day", "beach", List.of(video.getVideoId(), video.getVideoId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Beach day"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.duration").value(3))
                .andExpect(jsonPath("$.searchable").value(true))
                .andExpect(jsonPath("$.preSignedUrl").value(org.hamcrest.Matchers.containsString("montages/")));

        verify(aiClient, org.mockito.Mockito.times(1)).findIntervals("asset-1", "beach");
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        verify(aiClient).indexDocument(anyLong(), eq("MONTAGE"), anyLong(),
                eq("Beach day. beach. A sunny beach."));

        mvc.perform(as(get("/montages"), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].videos[0].previewUrl").value(org.hamcrest.Matchers.containsString("test-bucket")));
        mvc.perform(as(get("/montages/longest"), user)).andExpect(jsonPath("$.name").value("Beach day"));
        mvc.perform(as(get("/montages/most-vids"), user)).andExpect(jsonPath("$.name").value("Beach day"));
    }

    @Test
    void validatesTheRequest() throws Exception {
        TestUser user = newUser();
        Video video = saveVideo(user, "val-" + user.entity().getId(), "asset");
        String id = video.getVideoId();

        create(user, body("", "beach", List.of(id))).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("The montage name must be 1 to 60 characters."));
        create(user, body("ok", "x".repeat(101), List.of(id))).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("The topic must be 1 to 100 characters."));
        create(user, body("ok", "beach", List.of())).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("Select at least one video."));
        create(user, body("ok", "beach", List.of("a", "b", "c", "d", "e", "f"))).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("Select between 1 and 5 videos."));
        verify(aiClient, never()).findIntervals(anyString(), anyString());
    }

    @Test
    void onlyUsesTheCallersReadyVideos() throws Exception {
        TestUser user = newUser();
        TestUser other = newUser();
        Video othersVideo = saveVideo(other, "theirs-" + other.entity().getId(), "asset");
        Video processing = saveVideo(user, "proc-" + user.entity().getId(), null);

        create(user, body("m", "beach", List.of(othersVideo.getVideoId()))).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("One of the selected videos could not be found."));
        create(user, body("m", "beach", List.of(processing.getVideoId()))).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("still being processed")));
    }

    @Test
    void reportsWhenNothingMatchesOrAnalysisFails() throws Exception {
        TestUser user = newUser();
        Video video = saveVideo(user, "none-" + user.entity().getId(), "asset-none");

        when(aiClient.findIntervals("asset-none", "eiffel tower")).thenReturn("00:00-00:00");
        create(user, body("m", "eiffel tower", List.of(video.getVideoId()))).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("No moments matching \"eiffel tower\" were found in the selected videos."));

        when(aiClient.findIntervals("asset-none", "boats")).thenReturn(null);
        create(user, body("m", "boats", List.of(video.getVideoId()))).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.startsWith("Couldn't analyze")));

        // Intervals that are entirely past the end of the video
        when(aiClient.findIntervals("asset-none", "late")).thenReturn("00:50-00:59");
        create(user, body("m", "late", List.of(video.getVideoId()))).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void failsCleanlyWhenTheSourceCantBeDownloaded() throws Exception {
        TestUser user = newUser();
        Video video = saveVideo(user, "dl-" + user.entity().getId(), "asset-dl");
        when(aiClient.findIntervals("asset-dl", "beach")).thenReturn("00:01-00:03");
        when(s3Client.getObject(any(GetObjectRequest.class), any(Path.class))).thenThrow(new RuntimeException("s3 down"));

        create(user, body("m", "beach", List.of(video.getVideoId()))).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("Something went wrong while creating the montage. Please try again."));
        assertThat(montageRepo.findAllByUserUsername(user.email())).isEmpty();
    }

    @Test
    void enforcesTheMontageCountAndDailyBudgets() throws Exception {
        TestUser user = newUser();
        Video video = saveVideo(user, "cap-" + user.entity().getId(), "asset-cap");
        for (int i = 0; i < 10; i++) montageRepo.save(new Montage("m" + i, "p", user.entity(), i));
        create(user, body("m", "beach", List.of(video.getVideoId()))).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("You've reached the limit of 10 montages."));

        TestUser busy = newUser();
        Video busyVideo = saveVideo(busy, "busy-" + busy.entity().getId(), "asset-busy");
        usageService.consume(busy.entity().getId(), UsageService.Kind.ANALYZE, 30);
        create(busy, body("m", "beach", List.of(busyVideo.getVideoId()))).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("video analysis allowance")));
        verify(aiClient, never()).findIntervals(eq("asset-busy"), anyString());

        // A refused request doesn't use up any budget
        assertThatThrownBy(() -> usageService.consume(busy.entity().getId(), UsageService.Kind.MONTAGE, 11))
                .isInstanceOf(ApiException.class).hasMessageContaining("montage limit");
        usageService.consume(busy.entity().getId(), UsageService.Kind.MONTAGE, 9);
    }

    @Test
    void montagesAreRateLimitedPerUser() throws Exception {
        TestUser user = newUser();
        for (int i = 0; i < 5; i++) {
            create(user, body("", "x", List.of("v"))).andExpect(status().isUnprocessableEntity());
        }
        create(user, body("", "x", List.of("v"))).andExpect(status().isTooManyRequests());
    }

    @Test
    void deletesOnlyTheOwnersMontage() throws Exception {
        TestUser owner = newUser();
        TestUser other = newUser();
        Montage montage = new Montage("Old one", "p", owner.entity(), 3);
        Montage saved = montageRepo.save(montage); // no s3Key, made before keys were stored

        mvc.perform(as(get("/montages/longest"), other)).andExpect(content().string(""));
        mvc.perform(as(get("/montages/most-vids"), other)).andExpect(content().string(""));
        mvc.perform(as(delete("/montages/" + saved.getId()), other))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("That montage couldn't be found. It may already be deleted."));

        when(s3Client.deleteObject(any(software.amazon.awssdk.services.s3.model.DeleteObjectRequest.class)))
                .thenThrow(new RuntimeException("s3 down"));
        mvc.perform(as(delete("/montages/" + saved.getId()), owner))
                .andExpect(status().isNoContent());
        assertThat(montageRepo.findById(saved.getId())).isEmpty();
        assertThat(montageService.getS3Key(montage, "someone@test.dev")).isEqualTo("montages/someone/Old one.mp4");
    }

    @Test
    void pickersChooseTheLongestAndBiggestMontage() throws Exception {
        TestUser user = newUser();
        Video a = saveVideo(user, "pa-" + user.entity().getId(), "x");
        Video b = saveVideo(user, "pb-" + user.entity().getId(), "y");
        Montage small = new Montage("small", "p", user.entity(), 2);
        small.setVideos(new ArrayList<>(List.of(a, b)));
        montageRepo.save(small);
        montageRepo.save(new Montage("long", "p", user.entity(), 9));

        mvc.perform(as(get("/montages/longest"), user)).andExpect(jsonPath("$.name").value("long"));
        mvc.perform(as(get("/montages/most-vids"), user)).andExpect(jsonPath("$.name").value("small"));

        Montage built = new Montage("n", "p", List.of(a), 1, user.entity());
        built.setName("renamed");
        built.setPrompt("prompt");
        built.setDuration(4);
        assertThat(built.getVideos()).containsExactly(a);
        assertThat(built.getName()).isEqualTo("renamed");
        assertThat(built.getPrompt()).isEqualTo("prompt");
        assertThat(built.getDuration()).isEqualTo(4);
        assertThat(built.getUser()).isEqualTo(user.entity());
        assertThat(new Montage().getVideos()).isEmpty();
    }
}
