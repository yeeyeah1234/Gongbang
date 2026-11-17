package com.gongbang.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gongbang.contest.service.ContestCardService;
import com.gongbang.member.repository.BookmarkRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** MAIN-06 ③·MAIN-02 ④: 공모전 카드에 표시할 「내가 북마크한 공모전 id」 조회 */
class BookmarkServiceTest {

    private BookmarkRepository bookmarkRepository;
    private BookmarkService service;

    @BeforeEach
    void setUp() {
        bookmarkRepository = mock(BookmarkRepository.class);
        service = new BookmarkService(bookmarkRepository, mock(ContestCardService.class));
    }

    @Test
    void 북마크한_공모전_id만_돌려준다() {
        when(bookmarkRepository.findBookmarkedContestIds(1L, List.of(10L, 20L, 30L)))
                .thenReturn(List.of(10L, 30L));

        assertThat(service.findBookmarkedContestIds(1L, List.of(10L, 20L, 30L)))
                .containsExactlyInAnyOrder(10L, 30L);
    }

    @Test
    void 비로그인이면_DB를_조회하지_않고_빈_값() {
        assertThat(service.findBookmarkedContestIds(null, List.of(10L))).isEmpty();
        verify(bookmarkRepository, never()).findBookmarkedContestIds(anyLong(), any());
    }

    @Test
    void id_목록이_null이거나_비어_있으면_DB를_조회하지_않고_빈_값() {
        assertThat(service.findBookmarkedContestIds(1L, null)).isEmpty();
        assertThat(service.findBookmarkedContestIds(1L, List.of())).isEmpty();
        verify(bookmarkRepository, never()).findBookmarkedContestIds(anyLong(), any());
    }
}
