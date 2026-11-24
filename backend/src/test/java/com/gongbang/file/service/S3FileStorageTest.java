package com.gongbang.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

class S3FileStorageTest {

    @Test
    @DisplayName("S3 저장소는 지정 버킷과 키로 업로드하고 공개 URL을 조립하며 정리 시 deleteObject를 호출한다")
    void 저장_URL조립_삭제() throws Exception {
        S3Client client = mock(S3Client.class);
        S3FileStorage storage =
                new S3FileStorage(client, "gongbang-public", "https://cdn.example.com/files/");
        byte[] bytes = {1, 2, 3, 4};

        storage.store("chat/7/sample.png", "image/png", new MockMultipartFile("file", bytes));

        ArgumentCaptor<PutObjectRequest> requestCaptor =
                ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);
        verify(client).putObject(requestCaptor.capture(), bodyCaptor.capture());
        assertThat(requestCaptor.getValue().bucket()).isEqualTo("gongbang-public");
        assertThat(requestCaptor.getValue().key()).isEqualTo("chat/7/sample.png");
        assertThat(requestCaptor.getValue().contentType()).isEqualTo("image/png");
        try (var in = bodyCaptor.getValue().contentStreamProvider().newStream()) {
            assertThat(in.readAllBytes()).isEqualTo(bytes);
        }

        assertThat(storage.resolveUrl("/chat/7/sample.png"))
                .isEqualTo("https://cdn.example.com/files/chat/7/sample.png");

        storage.deleteQuietly("chat/7/sample.png");
        verify(client)
                .deleteObject(
                        DeleteObjectRequest.builder()
                                .bucket("gongbang-public")
                                .key("chat/7/sample.png")
                                .build());
    }

    @Test
    @DisplayName("S3 삭제 중 예외가 발생해도 deleteQuietly는 예외를 전파하지 않는다")
    void 삭제_실패_무시() {
        S3Client client = mock(S3Client.class);
        doThrow(new RuntimeException("network error"))
                .when(client)
                .deleteObject(any(DeleteObjectRequest.class));
        S3FileStorage storage =
                new S3FileStorage(client, "gongbang-public", "https://cdn.example.com/files");

        assertThatCode(() -> storage.deleteQuietly("chat/7/sample.png")).doesNotThrowAnyException();
        assertThatThrownBy(() -> storage.resolveUrl("../escape.png"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
