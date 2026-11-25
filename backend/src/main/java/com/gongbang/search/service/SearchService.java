package com.gongbang.search.service;

import com.gongbang.board.dto.BoardPostListItemResponse;
import com.gongbang.board.service.BoardPostQueryService;
import com.gongbang.contest.dto.ContestCardResponse;
import com.gongbang.contest.service.ContestQueryService;
import com.gongbang.global.common.PageResponse;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.domain.Member;
import com.gongbang.member.service.MemberService;
import com.gongbang.post.dto.PostListItemResponse;
import com.gongbang.post.service.PostQueryService;
import com.gongbang.search.domain.SearchType;
import com.gongbang.search.dto.SearchResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** SRCH-02·05 통합 검색: 한 검색어로 공모전·팀원 모집글·커뮤니티 게시글·작성자를 함께 찾는다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchService {

    // SRCH-03 ①: 앞뒤 공백을 지우고 2글자 이상
    static final int MIN_KEYWORD_LENGTH = 2;

    // 다른 도메인은 repository가 아니라 service로 부른다 (AGENTS.md 3장)
    private final ContestQueryService contestQueryService;
    private final PostQueryService postQueryService;
    private final BoardPostQueryService boardPostQueryService;
    private final MemberService memberService;

    public SearchResponse search(
            String keyword,
            String type,
            String sort,
            boolean includeClosed,
            int page,
            int size,
            Long memberId) {
        String trimmed = keyword == null ? "" : keyword.trim();
        if (trimmed.length() < MIN_KEYWORD_LENGTH) {
            throw new BusinessException(ErrorCode.KEYWORD_TOO_SHORT);
        }
        SearchType searchType = SearchType.from(type);

        // 「마감된 공모전 포함」은 공모전에만 적용한다. 모집완료·모집마감 모집글은 항상 넣는다 (SRCH-02 ①)
        PageResponse<ContestCardResponse> contests =
                searchType.includesContests()
                        ? contestQueryService.searchContests(
                                trimmed, sort, includeClosed, page, size, memberId)
                        : PageResponse.of(List.of(), page, size, 0);

        PageResponse<PostListItemResponse> posts = PageResponse.of(List.of(), page, size, 0);
        PageResponse<BoardPostListItemResponse> boardPosts =
                PageResponse.of(List.of(), page, size, 0);
        if (searchType.includesCommunity()) {
            // SRCH-05 ①: 제목·본문 일치 또는 닉네임이 맞는 회원이 쓴 글. 정렬 기준은 PST-001·BRD-001 (SRCH-02 ②)
            List<Long> authorIds = authorIdsByNickname(trimmed);
            posts = postQueryService.searchPosts(trimmed, authorIds, sort, page, size);
            boardPosts = boardPostQueryService.searchPosts(trimmed, authorIds, sort, page, size);
        }
        return new SearchResponse(contests, posts, boardPosts);
    }

    // SRCH-05 ① 작성자(닉네임) 검색. 지금은 닉네임이 정확히 같은 ACTIVE 회원만 찾는다.
    // TODO(확인 필요): 정진우 member service에 닉네임 부분 일치 메서드(ACTIVE, 최대 50명)가 생기면 그걸로 바꾼다
    private List<Long> authorIdsByNickname(String keyword) {
        return memberService.findByNickname(keyword).map(Member::getId).stream().toList();
    }
}
