package com.gongbang.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;

class FileStorageConfigTest {

    private final FileStorageConfig config = new FileStorageConfig();

    @Test
    @DisplayName("S3 설정이 유효하면 S3Client와 S3FileStorage를 생성한다")
    void 설정_정상_생성() {
        try (S3Client client =
                config.profileS3Client(
                        "https://s3.example.com", "garage", "access-key", "secret-key")) {
            FileStorage storage =
                    config.fileStorage(client, "gongbang-public", "https://cdn.example.com/files");
            assertThat(storage).isInstanceOf(S3FileStorage.class);
            assertThat(storage.resolveUrl("profile/1/a.png"))
                    .isEqualTo("https://cdn.example.com/files/profile/1/a.png");
        }
    }

    @Test
    @DisplayName("S3 접속 정보나 공개 URL이 올바르지 않으면 예외를 던진다")
    void 잘못된_설정_거부() {
        assertThatThrownBy(() -> config.profileS3Client("", "garage", "ak", "sk"))
                .isInstanceOf(IllegalArgumentException.class);

        try (S3Client client =
                config.profileS3Client("https://s3.example.com", "garage", "a", "b")) {
            assertThatThrownBy(() -> config.fileStorage(client, "", "https://cdn.example.com"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> config.fileStorage(client, "bucket", "ftp://cdn.example.com"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(
                            () ->
                                    config.fileStorage(
                                            client, "bucket", "https://cdn.example.com?q=1"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
