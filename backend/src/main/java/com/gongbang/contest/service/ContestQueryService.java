package com.gongbang.contest.service;

import com.gongbang.contest.domain.Category;
import com.gongbang.contest.domain.ContestSearchSort;
import com.gongbang.contest.domain.ContestSort;
import com.gongbang.contest.domain.ContestStatus;
import com.gongbang.contest.dto.CategoryResponse;
import com.gongbang.contest.dto.ContestCardResponse;
import com.gongbang.contest.dto.ContestSearchCondition;
import com.gongbang.contest.repository.CategoryRepository;
import com.gongbang.contest.repository.ContestQueryRepository;
import com.gongbang.global.common.PageResponse;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.service.BookmarkService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContestQueryService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    // 한 번에 받을 수 있는 최대 개수 (API 명세서 MAIN-01·SRCH-02 비고, 11/13 결정)
    static final int MAX_PAGE_SIZE = 50;

    private final ContestQueryRepository contestQueryRepository;
    private final CategoryRepository categoryRepository;
    // MAIN-06 ③: 북마크는 정진우 member 도메인 service로 확인한다 (AGENTS 3)
    private final BookmarkService bookmarkService;

    /**
     * MAIN-01·04·05 공모전 목록.
     *
     * @param memberId 로그인 회원 id (비로그인이면 null, 북마크 표시에 쓴다)
     */
    public PageResponse<ContestCardResponse> getContests(
            String status,
            String sort,
            String category,
            String keyword,
            int page,
            int size,
            Long memberId) {
        validatePage(page, size);
        ContestSearchCondition condition =
                new ContestSearchCondition(
                        ContestStatus.from(status),
                        ContestSort.from(sort),
                        StringUtils.hasText(category) ? category.trim() : null,
                        StringUtils.hasText(keyword) ? keyword.trim() : null);
        LocalDate today = LocalDate.now(KST);

        List<ContestCardResponse> items =
                contestQueryRepository
                        .findContests(condition, today, (long) page * size, size)
                        .stream()
                        .map(contest -> ContestCardResponse.of(contest, today))
                        .toList();
        long totalCount = contestQueryRepository.countContests(condition, today);
        return PageResponse.of(markBookmarks(items, memberId), page, size, totalCount);
    }

    /** SRCH-04 인기 공모전(추천): 진행 중인 공모전을 조회수 순으로. */
    public List<ContestCardResponse> getPopularContests(int size, Long memberId) {
        return getContests("active", "views", null, null, 0, size, memberId).items();
    }

    /**
     * SRCH-05 통합 검색의 공모전 부분: 제목·주최 부분 일치.
     *
     * @param keyword 앞뒤 공백을 지우고 길이 검사를 마친 검색어 (search 도메인이 검사)
     * @param includeClosed false면 진행 중인 공모전만 (SRCH-02 ①)
     * @param memberId 로그인 회원 id (비로그인이면 null)
     */
    public PageResponse<ContestCardResponse> searchContests(
            String keyword, String sort, boolean includeClosed, int page, int size, Long memberId) {
        validatePage(page, size);
        ContestSearchSort searchSort = ContestSearchSort.from(sort);
        ContestSearchCondition condition =
                new ContestSearchCondition(
                        includeClosed ? ContestStatus.ALL : ContestStatus.ACTIVE,
                        ContestSort.LATEST, // 검색은 searchSort로 정렬한다
                        null,
                        keyword);
        LocalDate today = LocalDate.now(KST);

        List<ContestCardResponse> items =
                contestQueryRepository
                        .searchContests(condition, searchSort, today, (long) page * size, size)
                        .stream()
                        .map(contest -> ContestCardResponse.of(contest, today))
                        .toList();
        long totalCount = contestQueryRepository.countContests(condition, today);
        return PageResponse.of(markBookmarks(items, memberId), page, size, totalCount);
    }

    /** MAIN-06 ③: 로그인 회원이면 이 페이지 카드 중 북마크한 것을 한 번에 조회해 표시한다. 비로그인은 모두 false. */
    private List<ContestCardResponse> markBookmarks(
            List<ContestCardResponse> items, Long memberId) {
        if (memberId == null || items.isEmpty()) {
            return items;
        }
        Set<Long> bookmarked =
                bookmarkService.findBookmarkedContestIds(
                        memberId, items.stream().map(ContestCardResponse::contestId).toList());
        return items.stream()
                .map(card -> card.withBookmark(bookmarked.contains(card.contestId())))
                .toList();
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    /** MAIN-05 분야 목록 (sort_order 순). */
    public List<CategoryResponse> getCategories() {
        return categoryRepository.findAll(Sort.by("sortOrder")).stream()
                .map(CategoryResponse::from)
                .toList();
    }

    /** 다른 도메인용: 분야 코드로 분야 ID를 찾는다 (PST-002 외부 공모전 분야). 없는 코드면 빈 값. */
    public Optional<Long> findCategoryId(String categoryCode) {
        return categoryRepository.findByCode(categoryCode).map(Category::getId);
    }
}
