package com.gongbang.search.service;

import com.gongbang.contest.dto.ContestCardResponse;
import com.gongbang.contest.service.ContestQueryService;
import com.gongbang.global.common.PageResponse;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.search.domain.SearchType;
import com.gongbang.search.dto.SearchResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** SRCH-02·05 통합 검색: 한 검색어로 공모전·모집글·작성자를 함께 찾는다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchService {

    // SRCH-03 ①: 앞뒤 공백을 지우고 2글자 이상
    static final int MIN_KEYWORD_LENGTH = 2;

    // 다른 도메인은 repository가 아니라 service로 부른다 (AGENTS.md 3장)
    private final ContestQueryService contestQueryService;

    public SearchResponse search(
            String keyword,
            String type,
            String sort,
            boolean includeClosed,
            int page,
            int size) {
        String trimmed = keyword == null ? "" : keyword.trim();
        if (trimmed.length() < MIN_KEYWORD_LENGTH) {
            throw new BusinessException(ErrorCode.KEYWORD_TOO_SHORT);
        }
        SearchType searchType = SearchType.from(type);

        PageResponse<ContestCardResponse> contests =
                searchType.includesContests()
                        ? contestQueryService.searchContests(
                                trimmed, sort, includeClosed, page, size)
                        : PageResponse.of(List.of(), page, size, 0);

        // TODO(확인 필요): 모집글 제목·본문, 작성자 닉네임 검색(SRCH-05 ①)은 모집글(김수형)·회원(정진우) 도메인이 생기면
        // 그 service를 불러 채운다. 정렬 sort는 모집글에도 같이 적용한다(마감 임박순은 모집 종료일 기준, SRCH-02 ②).
        PageResponse<Object> posts = PageResponse.of(List.of(), page, size, 0);

        return new SearchResponse(contests, posts);
    }
}
