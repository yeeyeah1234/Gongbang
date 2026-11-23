package com.gongbang.post.service;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * PST-007: app.profile-storage=s3이면 공모전 사진도 S3 공개 버킷에 둔다. S3 접속(S3Client)은 프로필 사진 설정(member
 * ProfileStorageConfig)이 만든 것을 같이 쓴다.
 */
@Configuration
@ConditionalOnProperty(name = "app.profile-storage", havingValue = "s3")
public class PostImageStorageConfig {

    @Bean
    PostImageStorage s3PostImageStorage(
            S3Client s3Client,
            @Value("${S3_PUBLIC_BUCKET}") String bucket,
            @Value("${FILE_PUBLIC_BASE_URL}") String base) {
        URI uri = URI.create(base);
        if (bucket.isBlank()
                || uri.getHost() == null
                || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                || uri.getQuery() != null
                || uri.getFragment() != null) {
            throw new IllegalArgumentException(
                    "Public post image storage configuration is invalid");
        }
        return new S3PostImageStorage(s3Client, bucket, base);
    }
}
