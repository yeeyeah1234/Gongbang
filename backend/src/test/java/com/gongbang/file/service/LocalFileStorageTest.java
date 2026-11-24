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
    @DisplayName("로컬 저장소는 upload-dir 아래에 파일을 저장하고 /uploads/{key} URL을 반환하며 삭제할 수 있다")
    void 저장_조회_삭제() throws Exception {
        LocalFileStorage storage = new LocalFileStorage(tempDir.toString());
        byte[] content = {1, 2, 3, 4};

        storage.store("chat/7/sample.png", "image/png", new MockMultipartFile("file", content));

        Path stored = tempDir.resolve("chat/7/sample.png");
        assertThat(Files.exists(stored)).isTrue();
        assertThat(Files.readAllBytes(stored)).isEqualTo(content);
        assertThat(storage.resolveUrl("chat/7/sample.png")).isEqualTo("/uploads/chat/7/sample.png");

        storage.deleteQuietly("chat/7/sample.png");
        assertThat(Files.exists(stored)).isFalse();
    }

    @Test
    @DisplayName("상위 경로 이탈 키(../)는 차단한다")
    void 경로_이탈_차단() {
        LocalFileStorage storage = new LocalFileStorage(tempDir.toString());

        assertThatThrownBy(
                        () ->
                                storage.store(
                                        "../secret.png",
                                        "image/png",
                                        new MockMultipartFile("file", new byte[] {1})))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
