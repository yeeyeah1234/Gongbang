package com.gongbang.member.service;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.s3.S3Client;

/** 프로필 저장소 전환 전까지 공용 {@link S3Client}(`file` 모듈)를 받아 {@link ProfileImageStorage}를 구성한다. */
@Configuration
@ConditionalOnProperty(name = "app.profile-storage", havingValue = "s3")
public class ProfileStorageConfig {

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
                || uri.getFragment() != null) {
            throw new IllegalArgumentException("Public profile storage configuration is invalid");
        }
        return new S3ProfileImageStorage(profileS3Client, bucket, base);
    }
}
