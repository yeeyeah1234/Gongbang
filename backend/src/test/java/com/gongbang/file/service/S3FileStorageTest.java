package com.gongbang.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    @DisplayName("S3 저장소는 업로드·공개 URL 생성·URL 기반 키 추출과 안전 삭제를 지원한다")
    void 저장_URL조립_추출_삭제() throws Exception {
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

        String url = storage.resolveUrl("/chat/7/sample.png");
        assertThat(url).isEqualTo("https://cdn.example.com/files/chat/7/sample.png");
        assertThat(storage.extractKey(url)).contains("chat/7/sample.png");

        // 다른 도메인·상위 경로·로컬 경로는 무시한다
        storage.deleteByUrlQuietly("https://other.example.com/files/profile/4/old.png");
        storage.deleteByUrlQuietly("https://cdn.example.com/files/../private/secret");
        storage.deleteByUrlQuietly("/uploads/profile/old.png");
        verify(client, never()).deleteObject(any(DeleteObjectRequest.class));

        storage.deleteByUrlQuietly(url);
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
        assertThat(storage.extractKey("https://cdn.example.com/files/")).isEmpty();
        assertThat(storage.extractKey("https://cdn.example.com/files/")).isEmpty();
        assertThatThrownBy(() -> storage.resolveUrl("../escape.png"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
