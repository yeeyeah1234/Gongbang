package com.gongbang.contest.service;

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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
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

    // TODO(확인 필요): 한 번에 받을 수 있는 최대 개수가 문서에 없어 임시로 정함
    static final int MAX_PAGE_SIZE = 100;

    private final ContestQueryRepository contestQueryRepository;
    private final CategoryRepository categoryRepository;

    /** MAIN-01·04·05 공모전 목록. */
    public PageResponse<ContestCardResponse> getContests(
            String status, String sort, String category, String keyword, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
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
        return PageResponse.of(items, page, size, totalCount);
    }

    /** MAIN-05 분야 목록 (sort_order 순). */
    public List<CategoryResponse> getCategories() {
        return categoryRepository.findAll(Sort.by("sortOrder")).stream()
                .map(CategoryResponse::from)
                .toList();
    }
}
