package com.gongbang.post.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExternalContestImageListenerTest {

    private static final ExternalContestLinkedEvent EVENT =
            new ExternalContestLinkedEvent(30L, "https://example.com/hack");

    @Mock private OgImageFetcher ogImageFetcher;
    @Mock private PostCommandService postCommandService;
    @InjectMocks private ExternalContestImageListener listener;

    @Test
    @DisplayName("대표 이미지를 불러오면 모집글에 저장한다")
    void 불러온_이미지를_저장한다() {
        given(ogImageFetcher.fetch("https://example.com/hack"))
                .willReturn(Optional.of("https://example.com/og.png"));

        listener.load(EVENT);

        verify(postCommandService)
                .saveExternalImage(30L, "https://example.com/hack", "https://example.com/og.png");
    }

    @Test
    @DisplayName("불러오지 못하면 저장하지 않는다 (화면은 기본 이미지)")
    void 못_불러오면_저장하지_않는다() {
        given(ogImageFetcher.fetch("https://example.com/hack")).willReturn(Optional.empty());

        listener.load(EVENT);

        verify(postCommandService, never()).saveExternalImage(any(), any(), any());
    }

    @Test
    @DisplayName("저장하다 실패해도 예외를 밖으로 던지지 않는다")
    void 저장_실패는_삼킨다() {
        given(ogImageFetcher.fetch("https://example.com/hack"))
                .willReturn(Optional.of("https://example.com/og.png"));
        willThrow(new IllegalStateException("db"))
                .given(postCommandService)
                .saveExternalImage(any(), any(), any());

        listener.load(EVENT);
    }
}
