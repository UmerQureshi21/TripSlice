package com.umerqureshicodes.tripslice.s3;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.File;
import java.nio.file.Path;
import java.net.URL;
import java.time.Duration;

// Every object lives in one bucket, configured with AWS_S3_BUCKET
@Service
public class S3Service {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucket;

    public S3Service(S3Client s3Client, S3Presigner s3Presigner, @Value("${aws.bucket.name}") String bucket) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.bucket = bucket;
    }

    public void putObject(String key, File file) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType("video/mp4")
                .build();
        s3Client.putObject(request, RequestBody.fromFile(file));
    }

    public void deleteObject(String key) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build());
    }

    /** Downloads an object to {@code target}, which must not exist yet. */
    public void downloadToFile(String key, Path target) {
        s3Client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build(), target);
    }

    public URL generatePresignedGetUrl(String key) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();
        PresignedGetObjectRequest presignedRequest =
                s3Presigner.presignGetObject(r -> r
                        .signatureDuration(Duration.ofMinutes(10))
                        .getObjectRequest(getObjectRequest)
                );
        return presignedRequest.url();
    }
}
