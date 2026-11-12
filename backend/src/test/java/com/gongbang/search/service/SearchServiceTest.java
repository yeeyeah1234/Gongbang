package com.gongbang.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gongbang.contest.service.ContestQueryService;
import com.gongbang.global.common.PageResponse;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.search.dto.SearchResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock private ContestQueryService contestQueryService;

    private SearchService searchService;

    @BeforeEach
    void setUp() {
        searchService = new SearchService(contestQueryService);
    }

    @Test
    @DisplayName("앞뒤 공백을 지운 검색어가 2글자 미만이면 400 KEYWORD_TOO_SHORT")
    void 검색어가_짧으면_400() {
        for (String keyword : new String[] {null, "", "   ", " a ", "가"}) {
            BusinessException e =
                    assertThrows(
                            BusinessException.class,
                            () -> searchService.search(keyword, "all", "accuracy", false, 0, 20));
            assertThat(e.getErrorCode()).isEqualTo(ErrorCode.KEYWORD_TOO_SHORT);
        }
    }

    @Test
    @DisplayName("결과 유형이 all|contest|post가 아니면 400 INVALID_INPUT")
    void 결과_유형이_잘못되면_400() {
        BusinessException e =
                assertThrows(
                        BusinessException.class,
                        () -> searchService.search("공모전", "member", "accuracy", false, 0, 20));

        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("공모전 검색에는 앞뒤 공백을 지운 검색어를 넘긴다")
    void 공백을_지운_검색어로_공모전을_찾는다() {
        given(contestQueryService.searchContests("청년 공모전", "latest", true, 1, 10))
                .willReturn(PageResponse.of(List.of(), 1, 10, 0));

        SearchResponse result = searchService.search("  청년 공모전  ", "contest", "latest", true, 1, 10);

        verify(contestQueryService).searchContests("청년 공모전", "latest", true, 1, 10);
        assertThat(result.posts().items()).isEmpty();
    }

    @Test
    @DisplayName("커뮤니티 탭이면 공모전은 찾지 않고 빈 목록을 준다")
    void 커뮤니티_탭은_공모전을_찾지_않는다() {
        SearchResponse result = searchService.search("공모전", "post", "accuracy", false, 0, 20);

        verify(contestQueryService, never())
                .searchContests(anyString(), anyString(), anyBoolean(), anyInt(), anyInt());
        assertThat(result.contests().items()).isEmpty();
        assertThat(result.contests().hasNext()).isFalse();
    }
}
