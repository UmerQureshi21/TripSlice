package com.umerqureshicodes.tripslice.s3;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

// The S3 client is mocked, presigning is local crypto so a real presigner with fake keys is used
class S3ServiceTest {

    @TempDir
    Path dir;

    private final S3Client s3 = mock(S3Client.class);
    private final S3Presigner presigner = S3Presigner.builder()
            .region(Region.US_EAST_1)
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("AKIATEST", "secret")))
            .build();
    private final S3Service service = new S3Service(s3, presigner, "my-bucket");

    @Test
    void usesTheConfiguredBucketForEveryCall() throws Exception {
        File file = dir.resolve("a.mp4").toFile();
        Files.writeString(file.toPath(), "data");

        service.putObject("videos/1/a.mp4", file);
        verify(s3).putObject(argThat((PutObjectRequest r) -> r.bucket().equals("my-bucket")
                && r.key().equals("videos/1/a.mp4") && r.contentType().equals("video/mp4")), any(RequestBody.class));

        service.deleteObject("videos/1/a.mp4");
        verify(s3).deleteObject(argThat((DeleteObjectRequest r) -> r.bucket().equals("my-bucket")));

        Path target = dir.resolve("download.mp4");
        service.downloadToFile("videos/1/a.mp4", target);
        verify(s3).getObject(argThat((GetObjectRequest r) -> r.bucket().equals("my-bucket")), eq(target));
    }

    @Test
    void presignedUrlsExpireAfterTenMinutes() {
        URL url = service.generatePresignedGetUrl("videos/1/a.mp4");
        assertThat(url.getHost()).startsWith("my-bucket.");
        assertThat(url.getQuery()).contains("X-Amz-Expires=600");
    }

    @Test
    void configUsesANamedProfileOnlyWhenOneIsSet() {
        S3Config config = new S3Config();
        assertThat(config.credentials()).isInstanceOf(software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider.class);
        ReflectionTestUtils.setField(config, "awsProfile", " ");
        assertThat(config.credentials()).isInstanceOf(software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider.class);
        ReflectionTestUtils.setField(config, "awsProfile", "tripslice-backend");
        assertThat(config.credentials()).isInstanceOf(software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider.class);
    }

    @Test
    void configBuildsClientsForTheRegion() {
        S3Config config = new S3Config();
        ReflectionTestUtils.setField(config, "awsRegion", "us-east-1");
        try (S3Client client = config.s3Client(); S3Presigner p = config.s3Presigner()) {
            assertThat(client.serviceClientConfiguration().region()).isEqualTo(Region.US_EAST_1);
            assertThat(p).isNotNull();
        }
    }
}
