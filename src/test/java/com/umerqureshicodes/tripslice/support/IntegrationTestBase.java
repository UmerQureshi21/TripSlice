package com.umerqureshicodes.tripslice.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.umerqureshicodes.tripslice.ai.AiClient;
import com.umerqureshicodes.tripslice.limits.DailyUsageRepo;
import com.umerqureshicodes.tripslice.limits.LimitRepo;
import com.umerqureshicodes.tripslice.montages.MontageRepo;
import com.umerqureshicodes.tripslice.users.AppUser;
import com.umerqureshicodes.tripslice.users.UserRepo;
import com.umerqureshicodes.tripslice.videos.Video;
import com.umerqureshicodes.tripslice.videos.VideoRepo;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Full application on a throwaway embedded Postgres (never the dev database). S3 and the
 * Python AI service are mocked, so no test touches AWS or spends TwelveLabs credits.
 * Every test makes its own users, so tests don't depend on each other's rows.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestBase.TestBeans.class)
public abstract class IntegrationTestBase {

    private static final EmbeddedPostgres POSTGRES;
    private static final AtomicInteger IP_COUNTER = new AtomicInteger(1);
    protected static final ObjectMapper JSON = new ObjectMapper();

    static {
        try {
            POSTGRES = EmbeddedPostgres.builder().start();
        } catch (IOException e) {
            throw new IllegalStateException("Could not start embedded Postgres", e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
        registry.add("jwt.secret", () -> "test-secret-test-secret-test-secret-test-secret-0123456789");
        registry.add("ai.service.key", () -> "test-service-key-test-service-key-0123");
        registry.add("ai.service.url", () -> "http://localhost:1");
        registry.add("aws.bucket.name", () -> "test-bucket");
        registry.add("turnstile.secret", () -> "");
        registry.add("frontend.host", () -> "http://localhost:5173");
        // Tests set a unique X-Real-IP per user, so per-IP limits don't leak between tests
        registry.add("ratelimit.client-ip-header", () -> "X-Real-IP");
        // The background job is run by hand in its own test
        registry.add("embeddings.job.initial-delay", () -> "86400000");
    }

    @TestConfiguration
    static class TestBeans {
        // Presigning is pure local crypto, so a real presigner with fake keys works offline
        @Bean
        @Primary
        S3Presigner testPresigner() {
            return S3Presigner.builder()
                    .region(Region.US_EAST_1)
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("AKIATEST", "secret")))
                    .build();
        }
    }

    @MockitoBean
    protected S3Client s3Client;
    @MockitoBean
    protected AiClient aiClient;

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected UserRepo userRepo;
    @Autowired
    protected VideoRepo videoRepo;
    @Autowired
    protected MontageRepo montageRepo;
    @Autowired
    protected LimitRepo limitRepo;
    @Autowired
    protected DailyUsageRepo dailyUsageRepo;

    // --- sample videos, made once with the local ffmpeg ---

    protected static Path samples;
    protected static File sampleMp4;

    @BeforeAll
    static void makeSamples() throws Exception {
        if (samples != null) return;
        samples = Files.createTempDirectory("tripslice-samples");
        sampleMp4 = samples.resolve("sample.mp4").toFile();
        assumeTrue(ffmpeg("testsrc=duration=6:size=160x120:rate=10", "libx264", sampleMp4), "ffmpeg not installed");
    }

    protected static boolean ffmpeg(String source, String codec, File out) {
        try {
            Process p = new ProcessBuilder("ffmpeg", "-v", "error", "-y", "-f", "lavfi", "-i", source,
                    "-c:v", codec, "-pix_fmt", "yuv420p", out.getAbsolutePath())
                    .redirectErrorStream(true).start();
            return p.waitFor(120, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    // --- users and requests ---

    protected record TestUser(String email, String password, String token, String ip, AppUser entity) {}

    protected static String uniqueIp() {
        int n = IP_COUNTER.getAndIncrement();
        return "10." + ((n >> 16) & 255) + "." + ((n >> 8) & 255) + "." + (n & 255);
    }

    protected MockHttpServletRequestBuilder from(MockHttpServletRequestBuilder request, String ip) {
        return request.header("X-Real-IP", ip);
    }

    protected MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, TestUser user) {
        return request.header("Authorization", user.token()).header("X-Real-IP", user.ip());
    }

    protected TestUser newUser() throws Exception {
        String id = UUID.randomUUID().toString().substring(0, 8);
        String email = "user-" + id + "@test.dev";
        String password = "password-" + id;
        String ip = uniqueIp();
        mvc.perform(from(post("/register"), ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of(
                                "username", email, "password", password, "displayedName", "name-" + id))))
                .andReturn();
        MvcResult login = login(email, password, ip);
        return new TestUser(email, password, login.getResponse().getHeader("Authorization"), ip,
                userRepo.findByUsername(email).orElseThrow());
    }

    protected MvcResult login(String email, String password, String ip) throws Exception {
        return mvc.perform(from(post("/generate-token"), ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("username", email, "password", password))))
                .andReturn();
    }

    protected static Cookie refreshCookie(MvcResult login) {
        String header = login.getResponse().getHeader("Set-Cookie");
        String value = header.substring(header.indexOf('=') + 1, header.indexOf(';'));
        return new Cookie("refreshToken", value);
    }

    protected Video saveVideo(TestUser user, String videoId, String assetId) {
        Video video = new Video(videoId, "clip-" + videoId + ".mp4", user.entity());
        video.setAssetId(assetId);
        video.setS3Key("videos/" + user.entity().getId() + "/" + videoId + ".mp4");
        return videoRepo.save(video);
    }
}
