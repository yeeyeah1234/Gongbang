package com.gongbang.comment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gongbang.comment.repository.CommentRepository;
import com.gongbang.comment.repository.CommentRepository.CommentTargetCount;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommentCountServiceTest {

    @Mock private CommentRepository commentRepository;

    private CommentCountService countService;

    @BeforeEach
    void setUp() {
        countService = new CommentCountService(commentRepository);
    }

    @Test
    @DisplayName("countByPostIds·countByBoardPostIds는 ID 목록으로 일괄 집계하고 비어 있으면 DB를 부르지 않는다")
    void 목록_댓글수_일괄_조회() {
        assertThat(countService.countByPostIds(null)).isEmpty();
        assertThat(countService.countByPostIds(List.of())).isEmpty();
        assertThat(countService.countByBoardPostIds(Arrays.asList((Long) null))).isEmpty();
        verify(commentRepository, never()).countActiveByPostIds(any());
        verify(commentRepository, never()).countActiveByBoardPostIds(any());

        given(commentRepository.countActiveByPostIds(Set.of(10L, 11L)))
                .willReturn(List.of(targetCount(10L, 3L)));
        given(commentRepository.countActiveByBoardPostIds(Set.of(20L, 21L)))
                .willReturn(List.of(targetCount(20L, 5L), targetCount(21L, 1L)));

        assertThat(countService.countByPostIds(Arrays.asList(10L, 11L, 10L, null)))
                .containsExactlyInAnyOrderEntriesOf(Map.of(10L, 3L));
        assertThat(countService.countByBoardPostIds(List.of(20L, 21L)))
                .containsExactlyInAnyOrderEntriesOf(Map.of(20L, 5L, 21L, 1L));
    }

    private static CommentTargetCount targetCount(Long targetId, long count) {
        return new CommentTargetCount() {
            @Override
            public Long getTargetId() {
                return targetId;
            }

            @Override
            public long getCount() {
                return count;
            }
        };
    }
}
