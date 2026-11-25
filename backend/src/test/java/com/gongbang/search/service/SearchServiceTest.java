package com.gongbang.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gongbang.board.service.BoardPostQueryService;
import com.gongbang.contest.service.ContestQueryService;
import com.gongbang.global.common.PageResponse;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.domain.Member;
import com.gongbang.member.service.MemberService;
import com.gongbang.post.service.PostQueryService;
import com.gongbang.search.dto.SearchResponse;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock private ContestQueryService contestQueryService;
    @Mock private PostQueryService postQueryService;
    @Mock private BoardPostQueryService boardPostQueryService;
    @Mock private MemberService memberService;

    private SearchService searchService;

    @BeforeEach
    void setUp() {
        searchService =
                new SearchService(
                        contestQueryService,
                        postQueryService,
                        boardPostQueryService,
                        memberService);
    }

    private void givenEmptyCommunity() {
        given(postQueryService.searchPosts(anyString(), any(), anyString(), anyInt(), anyInt()))
                .willReturn(PageResponse.of(List.of(), 0, 20, 0));
        given(
                        boardPostQueryService.searchPosts(
                                anyString(), any(), anyString(), anyInt(), anyInt()))
                .willReturn(PageResponse.of(List.of(), 0, 20, 0));
    }

    @Test
    @DisplayName("앞뒤 공백을 지운 검색어가 2글자 미만이면 400 KEYWORD_TOO_SHORT")
    void 검색어가_짧으면_400() {
        for (String keyword : new String[] {null, "", "   ", " a ", "가"}) {
            BusinessException e =
                    assertThrows(
                            BusinessException.class,
                            () ->
                                    searchService.search(
                                            keyword, "all", "accuracy", false, 0, 20, null));
            assertThat(e.getErrorCode()).isEqualTo(ErrorCode.KEYWORD_TOO_SHORT);
        }
    }

    @Test
    @DisplayName("결과 유형이 all|contest|post가 아니면 400 INVALID_INPUT")
    void 결과_유형이_잘못되면_400() {
        BusinessException e =
                assertThrows(
                        BusinessException.class,
                        () ->
                                searchService.search(
                                        "공모전", "member", "accuracy", false, 0, 20, null));

        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("공모전 검색에는 앞뒤 공백을 지운 검색어와 로그인 회원 id를 넘긴다")
    void 공백을_지운_검색어로_공모전을_찾는다() {
        given(contestQueryService.searchContests("청년 공모전", "latest", true, 1, 10, 7L))
                .willReturn(PageResponse.of(List.of(), 1, 10, 0));

        SearchResponse result =
                searchService.search("  청년 공모전  ", "contest", "latest", true, 1, 10, 7L);

        verify(contestQueryService).searchContests("청년 공모전", "latest", true, 1, 10, 7L);
        assertThat(result.posts().items()).isEmpty();
        assertThat(result.boardPosts().items()).isEmpty();
    }

    @Test
    @DisplayName("공모전 탭이면 모집글·게시글·작성자는 찾지 않는다")
    void 공모전_탭은_커뮤니티를_찾지_않는다() {
        given(contestQueryService.searchContests("공모전", "accuracy", false, 0, 20, null))
                .willReturn(PageResponse.of(List.of(), 0, 20, 0));

        searchService.search("공모전", "contest", "accuracy", false, 0, 20, null);

        verify(postQueryService, never())
                .searchPosts(anyString(), any(), anyString(), anyInt(), anyInt());
        verify(boardPostQueryService, never())
                .searchPosts(anyString(), any(), anyString(), anyInt(), anyInt());
        verify(memberService, never()).findByNickname(anyString());
    }

    @Test
    @DisplayName("커뮤니티 검색에는 닉네임이 같은 회원 id를 작성자 조건으로 같이 넘긴다")
    void 닉네임이_같은_회원의_글도_찾는다() {
        Member author = Member.createEmailMember("a@test.local", "hash", "기획왕");
        ReflectionTestUtils.setField(author, "id", 42L);
        given(memberService.findByNickname("기획왕")).willReturn(Optional.of(author));
        givenEmptyCommunity();

        searchService.search(" 기획왕 ", "post", "deadline", false, 2, 10, null);

        verify(postQueryService).searchPosts("기획왕", List.of(42L), "deadline", 2, 10);
        verify(boardPostQueryService).searchPosts("기획왕", List.of(42L), "deadline", 2, 10);
    }

    @Test
    @DisplayName("닉네임이 맞는 회원이 없으면 작성자 조건 없이 제목·본문으로만 찾는다")
    void 닉네임이_없으면_작성자_조건은_비운다() {
        given(memberService.findByNickname("데이터 분석")).willReturn(Optional.empty());
        givenEmptyCommunity();

        searchService.search("데이터 분석", "post", "accuracy", false, 0, 20, null);

        verify(postQueryService).searchPosts("데이터 분석", List.of(), "accuracy", 0, 20);
        verify(boardPostQueryService).searchPosts("데이터 분석", List.of(), "accuracy", 0, 20);
    }

    @Test
    @DisplayName("전체 탭이면 공모전과 커뮤니티를 함께 찾고, 마감된 공모전 포함은 공모전에만 넘긴다")
    void 전체_탭은_모두_찾는다() {
        given(contestQueryService.searchContests("공모전", "latest", true, 0, 20, 7L))
                .willReturn(PageResponse.of(List.of(), 0, 20, 0));
        given(memberService.findByNickname("공모전")).willReturn(Optional.empty());
        givenEmptyCommunity();

        SearchResponse result = searchService.search("공모전", "all", "latest", true, 0, 20, 7L);

        verify(contestQueryService).searchContests("공모전", "latest", true, 0, 20, 7L);
        verify(postQueryService).searchPosts("공모전", List.of(), "latest", 0, 20);
        verify(boardPostQueryService).searchPosts("공모전", List.of(), "latest", 0, 20);
        assertThat(result.posts()).isNotNull();
        assertThat(result.boardPosts()).isNotNull();
    }

    @Test
    @DisplayName("커뮤니티 탭이면 공모전은 찾지 않고 빈 목록을 준다")
    void 커뮤니티_탭은_공모전을_찾지_않는다() {
        given(memberService.findByNickname("공모전")).willReturn(Optional.empty());
        givenEmptyCommunity();

        SearchResponse result = searchService.search("공모전", "post", "accuracy", false, 0, 20, null);

        verify(contestQueryService, never())
                .searchContests(anyString(), anyString(), anyBoolean(), anyInt(), anyInt(), any());
        assertThat(result.contests().items()).isEmpty();
        assertThat(result.contests().hasNext()).isFalse();
    }
}
