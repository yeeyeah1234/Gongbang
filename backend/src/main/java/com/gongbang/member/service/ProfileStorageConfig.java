package com.gongbang.member.service;

import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@ConditionalOnProperty(name = "app.profile-storage", havingValue = "s3")
public class ProfileStorageConfig {
    @Bean(destroyMethod = "close")
    S3Client profileS3Client(
            @Value("${S3_ENDPOINT}") String endpoint,
            @Value("${S3_REGION}") String region,
            @Value("${S3_ACCESS_KEY}") String access,
            @Value("${S3_SECRET_KEY}") String secret) {
        if (endpoint.isBlank() || region.isBlank() || access.isBlank() || secret.isBlank())
            throw new IllegalArgumentException("S3 profile storage configuration is incomplete");
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .forcePathStyle(true)
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(access, secret)))
                .overrideConfiguration(
                        c ->
                                c.apiCallTimeout(Duration.ofSeconds(20))
                                        .apiCallAttemptTimeout(Duration.ofSeconds(8)))
                .build();
    }

    @Bean
    ProfileImageStorage s3ProfileImageStorage(
            S3Client profileS3Client,
            @Value("${S3_PUBLIC_BUCKET}") String bucket,
            @Value("${FILE_PUBLIC_BASE_URL}") String base) {
        URI uri = URI.create(base);
        if (bucket.isBlank()
                || uri.getHost() == null
                || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                || uri.getQuery() != null
                || uri.getFragment() != null)
            throw new IllegalArgumentException("Public profile storage configuration is invalid");
        return new S3ProfileImageStorage(profileS3Client, bucket, base);
    }
}
