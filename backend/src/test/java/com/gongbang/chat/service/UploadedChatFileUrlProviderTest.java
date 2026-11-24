package com.gongbang.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.gongbang.file.service.FileUploadService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UploadedChatFileUrlProviderTest {

    @Mock private FileUploadService fileUploadService;
    @InjectMocks private UploadedChatFileUrlProvider provider;

    @Test
    @DisplayName("ChatFileUrlProvider 구현체는 FileUploadService로 파일 접근 URL을 위임 조회한다")
    void 파일_URL_위임_조회() {
        given(fileUploadService.getFileUrls(List.of(10L, 20L)))
                .willReturn(
                        Map.of(
                                10L, "/uploads/chat/7/1.png",
                                20L, "/uploads/chat/8/2.jpg"));

        assertThat(provider.getFileUrls(List.of(10L, 20L)))
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of(
                                10L, "/uploads/chat/7/1.png",
                                20L, "/uploads/chat/8/2.jpg"));
    }
}
