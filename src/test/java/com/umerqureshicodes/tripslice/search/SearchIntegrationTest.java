package com.umerqureshicodes.tripslice.search;

import com.umerqureshicodes.tripslice.ai.AiSearchHit;
import com.umerqureshicodes.tripslice.limits.UsageService;
import com.umerqureshicodes.tripslice.montages.Montage;
import com.umerqureshicodes.tripslice.support.IntegrationTestBase;
import com.umerqureshicodes.tripslice.videos.Video;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SearchIntegrationTest extends IntegrationTestBase {

    @Autowired
    UsageService usageService;
    @Autowired
    SearchIndexJob job;

    @Test
    void findsTheCallersVideosAndMontagesOnly() throws Exception {
        TestUser user = newUser();
        TestUser other = newUser();
        Video mine = saveVideo(user, "s-" + user.entity().getId(), "a");
        Video theirs = saveVideo(other, "s-" + other.entity().getId(), "b");
        Montage montage = montageRepo.save(new Montage("Sunset", "sunset", user.entity(), 4));
        long userId = user.entity().getId();

        // A stale embedding pointing at someone else's video is dropped by the owner check
        when(aiClient.search(userId, "VIDEO", "beach", 10)).thenReturn(List.of(
                new AiSearchHit(mine.getId(), "a beach", 0.9), new AiSearchHit(theirs.getId(), "theirs", 0.8)));
        when(aiClient.search(userId, "MONTAGE", "sunset", 10)).thenReturn(List.of(
                new AiSearchHit(montage.getId(), "sunset text", 0.7)));

        mvc.perform(as(get("/search").param("q", " beach "), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].kind").value("VIDEO"))
                .andExpect(jsonPath("$[0].id").value(mine.getId()));
        mvc.perform(as(get("/search").param("q", "sunset").param("type", "montages"), user))
                .andExpect(jsonPath("$[0].kind").value("MONTAGE"))
                .andExpect(jsonPath("$[0].prompt").value("sunset"));
    }

    @Test
    void validatesAndBudgetsQueries() throws Exception {
        TestUser user = newUser();
        mvc.perform(as(get("/search").param("q", "  "), user)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(as(get("/search").param("q", "x".repeat(201)), user))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Searches can be at most 200 characters."));

        usageService.consume(user.entity().getId(), UsageService.Kind.SEARCH, 100);
        mvc.perform(as(get("/search").param("q", "beach"), user))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("You've reached today's search limit. Please try again tomorrow."));
        verify(aiClient, never()).search(eq(user.entity().getId()), anyString(), anyString(), anyInt());
    }

    @Test
    void searchIsRateLimitedPerUser() throws Exception {
        TestUser user = newUser();
        for (int i = 0; i < 30; i++) {
            mvc.perform(as(get("/search").param("q", " "), user)).andExpect(status().isOk());
        }
        mvc.perform(as(get("/search").param("q", " "), user)).andExpect(status().isTooManyRequests());
    }

    @Test
    void backgroundJobSummarizesAndEmbeds() throws Exception {
        // Mark rows from other tests as done (embedded test DB only), so the job's "top 3" picks just these
        for (Video v : videoRepo.findAll()) { v.setEmbedded(true); videoRepo.save(v); }
        for (Montage m : montageRepo.findAll()) { m.setEmbedded(true); montageRepo.save(m); }

        TestUser user = newUser();
        Video notIndexed = saveVideo(user, "job-a-" + user.entity().getId(), null);
        Video noSummary = saveVideo(user, "job-b-" + user.entity().getId(), "asset-b");
        Video ready = saveVideo(user, "job-c-" + user.entity().getId(), "asset-c");
        Montage montage = new Montage("job montage", "p", user.entity(), 2);
        montage.setVideos(new ArrayList<>(List.of(ready)));
        montage = montageRepo.save(montage);

        when(aiClient.getAssetId(notIndexed.getVideoId())).thenReturn(null);
        when(aiClient.summarize("asset-b")).thenReturn(null);
        when(aiClient.summarize("asset-c")).thenReturn("Waves at dusk.");
        when(aiClient.indexDocument(anyLong(), anyString(), anyLong(), anyString())).thenReturn(true);

        job.run();

        assertThat(videoRepo.findById(notIndexed.getId()).orElseThrow().getSummaryAttempts()).isEqualTo(1);
        assertThat(videoRepo.findById(noSummary.getId()).orElseThrow().isEmbedded()).isFalse();
        Video readyAfter = videoRepo.findById(ready.getId()).orElseThrow();
        assertThat(readyAfter.isEmbedded()).isTrue();
        assertThat(readyAfter.getSummary()).isEqualTo("Waves at dusk.");
        assertThat(montageRepo.findById(montage.getId()).orElseThrow().isEmbedded()).isTrue();

        // A video that has a summary but no embedding only needs embedding, no new analysis
        Video described = videoRepo.findById(noSummary.getId()).orElseThrow();
        described.setSummary("Already described.");
        videoRepo.save(described);
        when(aiClient.getAssetId(notIndexed.getVideoId())).thenReturn("asset-a");
        when(aiClient.summarize("asset-a")).thenReturn("City lights.");
        job.run();
        assertThat(videoRepo.findById(noSummary.getId()).orElseThrow().isEmbedded()).isTrue();
        assertThat(videoRepo.findById(notIndexed.getId()).orElseThrow().getAssetId()).isEqualTo("asset-a");
    }
}
