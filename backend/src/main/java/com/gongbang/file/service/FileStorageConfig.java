package com.gongbang.file.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;
import software.amazon.awssdk.services.s3.S3Client;

/** `app.profile-storage=s3`일 때 기존 Garage {@link S3Client} 설정을 공유해 공용 파일 저장소를 구성한다. */
@Configuration
@ConditionalOnProperty(name = "app.profile-storage", havingValue = "s3")
public class FileStorageConfig {

    @Bean
    FileStorage fileStorage(
            S3Client profileS3Client,
            @Value("${S3_PUBLIC_BUCKET:}") String bucket,
            @Value("${FILE_PUBLIC_BASE_URL:}") String base) {
        Assert.hasText(bucket, "S3_PUBLIC_BUCKET must not be blank when profile-storage=s3");
        Assert.hasText(base, "FILE_PUBLIC_BASE_URL must not be blank when profile-storage=s3");
        return new S3FileStorage(profileS3Client, bucket, base);
    }
}
