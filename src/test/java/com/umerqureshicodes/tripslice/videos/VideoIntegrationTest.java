package com.umerqureshicodes.tripslice.videos;

import com.umerqureshicodes.tripslice.limits.Limit;
import com.umerqureshicodes.tripslice.montages.Montage;
import com.umerqureshicodes.tripslice.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VideoIntegrationTest extends IntegrationTestBase {

    static File webm;
    static File tooShort;
    static File tooLong;
    static File tooWide;

    @BeforeAll
    static void makeMoreSamples() {
        webm = samples.resolve("sample.webm").toFile();
        tooShort = samples.resolve("short.mp4").toFile();
        tooLong = samples.resolve("long.mp4").toFile();
        tooWide = samples.resolve("wide.mp4").toFile();
        assumeTrue(ffmpeg("testsrc=duration=4:size=160x120:rate=10", "libvpx", webm));
        assumeTrue(ffmpeg("testsrc=duration=2:size=160x120:rate=10", "libx264", tooShort));
        assumeTrue(ffmpeg("testsrc=duration=121:size=16x16:rate=1", "libx264", tooLong));
        assumeTrue(ffmpeg("testsrc=duration=4:size=4100x16:rate=1", "libx264", tooWide));
    }

    @AfterEach
    void resetGlobalLimit() {
        limitRepo.deleteAll(); // only the singleton counter row, in the embedded test database
    }

    private MockMultipartFile part(File file, String filename, String contentType) throws Exception {
        return new MockMultipartFile("files", filename, contentType, Files.readAllBytes(file.toPath()));
    }

    private MockMultipartHttpServletRequestBuilder upload(MockMultipartFile... files) {
        MockMultipartHttpServletRequestBuilder builder = multipart("/videos");
        for (MockMultipartFile f : files) builder.file(f);
        return builder;
    }

    @Test
    void uploadsAnMp4AndAConvertedWebm() throws Exception {
        TestUser user = newUser();
        when(aiClient.indexVideo(anyString())).thenReturn("tl-1", "tl-2");

        mvc.perform(as(upload(part(sampleMp4, "beach.mp4", "video/mp4"),
                        part(webm, "city\u0007/../.webm", "video/webm")), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("beach.mp4"))
                .andExpect(jsonPath("$[1].name").value("city...webm"));

        verify(s3Client, org.mockito.Mockito.times(2)).putObject(
                any(software.amazon.awssdk.services.s3.model.PutObjectRequest.class),
                any(software.amazon.awssdk.core.sync.RequestBody.class));
        assertThat(videoRepo.findAllByUserUsername(user.email())).hasSize(2);
        assertThat(limitRepo.findById(1L).orElseThrow().getTotalVideoMillis()).isBetween(9_000L, 11_000L);

        mvc.perform(as(get("/videos"), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].previewUrl").value(org.hamcrest.Matchers.containsString("test-bucket")))
                .andExpect(jsonPath("$[0].id").isNumber());
    }

    @Test
    void refusesFilesThatAreNotVideos() throws Exception {
        TestUser user = newUser();
        MockMultipartFile text = new MockMultipartFile("files", "notes.mp4", "video/mp4", "hello".getBytes());
        mvc.perform(as(upload(text), user))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unsupported or corrupt video file"));
        verify(aiClient, never()).indexVideo(anyString());
    }

    @Test
    void refusesBadDurationsAndHugeFramesBeforeConverting() throws Exception {
        TestUser user = newUser();
        mvc.perform(as(upload(part(tooShort, "short.mp4", "video/mp4")), user))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("\"short.mp4\" is too long or too short. Each video must be between 3 seconds and 2 minutes."));
        mvc.perform(as(upload(part(tooLong, "long.mov", "video/quicktime")), user))
                .andExpect(status().isBadRequest());
        mvc.perform(as(upload(part(tooWide, "wide.mp4", "video/mp4")), user))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("\"wide.mp4\" is larger than 4K. Please upload a smaller version."));
    }

    @Test
    void enforcesFileCountLimits() throws Exception {
        TestUser user = newUser();
        MockMultipartFile f = part(sampleMp4, "a.mp4", "video/mp4");
        mvc.perform(as(upload(f, f, f, f, f, f), user))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("You can upload up to 5 videos at a time."));

        for (int i = 0; i < 15; i++) saveVideo(user, "existing-" + user.entity().getId() + "-" + i, null);
        mvc.perform(as(upload(f), user))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("You can have at most 15 videos. Delete some to upload more."));
    }

    @Test
    void refusesUploadsOnceTheGlobalMinutesAreUsedUp() throws Exception {
        TestUser user = newUser();
        limitRepo.save(new Limit(360L * 60 * 1000 - 1000));
        mvc.perform(as(upload(part(sampleMp4, "a.mp4", "video/mp4")), user))
                .andExpect(status().isTooManyRequests());
        verify(aiClient, never()).indexVideo(anyString());
    }

    @Test
    void cleansUpS3WhenIndexingFails() throws Exception {
        TestUser user = newUser();
        when(aiClient.indexVideo(anyString())).thenReturn(null);
        mvc.perform(as(upload(part(sampleMp4, "a.mp4", "video/mp4")), user))
                .andExpect(status().isBadGateway());
        verify(s3Client).deleteObject(any(software.amazon.awssdk.services.s3.model.DeleteObjectRequest.class));
        assertThat(videoRepo.findAllByUserUsername(user.email())).isEmpty();
        // TwelveLabs indexed nothing, so the reserved minutes were given back
        assertThat(limitRepo.findById(1L).orElseThrow().getTotalVideoMillis()).isZero();
    }

    @Test
    void deletesOnlyTheOwnersVideo() throws Exception {
        TestUser owner = newUser();
        TestUser other = newUser();
        Video video = saveVideo(owner, "del-" + owner.entity().getId(), "asset");
        Montage montage = new Montage("m", "p", owner.entity(), 3);
        montage.setVideos(new java.util.ArrayList<>(List.of(video)));
        montageRepo.save(montage);

        mvc.perform(as(delete("/videos/" + video.getId()), other))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("That video couldn't be found. It may already be deleted."));

        // S3 failing doesn't stop the delete
        doThrow(new RuntimeException("s3 down")).when(s3Client)
                .deleteObject(any(software.amazon.awssdk.services.s3.model.DeleteObjectRequest.class));
        mvc.perform(as(delete("/videos/" + video.getId()), owner))
                .andExpect(status().isNoContent());
        assertThat(videoRepo.findById(video.getId())).isEmpty();
        verify(aiClient).deleteVideo(video.getVideoId());
    }

    @Test
    void videosFromBeforeS3KeysUseTheirOldPath() {
        Video legacy = new Video("tl-old", "paris.mp4", null);
        assertThat(new VideoService(null, null, null, null, null, null, null).getS3Name(legacy))
                .isEqualTo("test/paris.mp4-tl-old");
        assertThat(legacy.getMontages()).isEmpty();
        legacy.setName("renamed");
        legacy.setVideoId("tl-new");
        legacy.setId(5L);
        legacy.setSummary("sum");
        assertThat(legacy.getName()).isEqualTo("renamed");
        assertThat(legacy.getVideoId()).isEqualTo("tl-new");
        assertThat(new Video("v", "n", List.of(), null).getMontages()).isEmpty();
        assertThat(new VideoRequestDTO("n", "v").getName()).isEqualTo("n");
        VideoRequestDTO dto = new VideoRequestDTO("n", "v");
        dto.setName("x");
        dto.setVideoId("y");
        assertThat(dto.getVideoId()).isEqualTo("y");
        assertThat(dto.getName()).isEqualTo("x");
    }
}
