package com.gongbang.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class LocalFileStorageTest {

    @TempDir Path tempDir;

    @Test
    @DisplayName("로컬 저장소는 upload-dir 아래에 파일을 저장하고 /uploads/{key} URL 변환·추출·삭제를 지원한다")
    void 저장_조회_삭제() throws Exception {
        LocalFileStorage storage = new LocalFileStorage(tempDir.toString());
        byte[] content = {1, 2, 3, 4};

        storage.store("chat/7/sample.png", "image/png", new MockMultipartFile("file", content));

        Path stored = tempDir.resolve("chat/7/sample.png");
        assertThat(Files.exists(stored)).isTrue();
        assertThat(Files.readAllBytes(stored)).isEqualTo(content);
        String url = storage.resolveUrl("chat/7/sample.png");
        assertThat(url).isEqualTo("/uploads/chat/7/sample.png");
        assertThat(storage.extractKey(url)).contains("chat/7/sample.png");

        storage.deleteByUrlQuietly(url);
        assertThat(Files.exists(stored)).isFalse();
    }

    @Test
    @DisplayName("외부 URL이나 경로 이탈 시도 URL에서는 키를 추출하지 않고 삭제도 무시한다")
    void URL_검증_및_경로이탈_차단() {
        LocalFileStorage storage = new LocalFileStorage(tempDir.toString());

        assertThat(storage.extractKey(null)).isEmpty();
        assertThat(storage.extractKey("/uploads/")).isEmpty();
        assertThat(storage.extractKey("https://k.kakaocdn.net/profile.jpg")).isEmpty();
        assertThat(storage.extractKey("/uploads/../secret.png")).isEmpty();
        assertThat(storage.extractKey("/uploads/chat/7/sample.png?v=1")).isEmpty();

        assertThatThrownBy(
                        () ->
                                storage.store(
                                        "../secret.png",
                                        "image/png",
                                        new MockMultipartFile("file", new byte[] {1})))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
