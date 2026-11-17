package com.gongbang.contest.service;

import com.gongbang.contest.domain.Category;
import com.gongbang.contest.domain.Contest;
import com.gongbang.contest.dto.ContestSummary;
import com.gongbang.contest.repository.CategoryRepository;
import com.gongbang.contest.repository.ContestRepository;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다른 도메인이 공모전·분야를 읽을 때 쓰는 조회 (AGENTS.md 3: 다른 도메인 repository를 직접 부르지 않음). 조회수는 올리지
 * 않는다. 없는 ID는 결과에서 빠진다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContestLookupService {

    private final ContestRepository contestRepository;
    private final CategoryRepository categoryRepository;

    /** 공모전 ID → 요약. 목록 한 페이지의 공모전을 한 번에 읽는다 (PST-001). */
    public Map<Long, ContestSummary> findSummaries(Collection<Long> contestIds) {
        if (contestIds.isEmpty()) {
            return Map.of();
        }
        return contestRepository.findAllWithCategoryByIdIn(contestIds).stream()
                .collect(Collectors.toMap(Contest::getId, ContestSummary::from));
    }

    /** 공모전 하나의 요약 (PST-005 상세). 없으면 빈 값. */
    public Optional<ContestSummary> findSummary(Long contestId) {
        return contestRepository.findWithCategoryById(contestId).map(ContestSummary::from);
    }

    /** 분야 ID → 분야 이름. 외부 공모전 분야 표시에 쓴다 (PST-001). */
    public Map<Long, String> findCategoryNames(Collection<Long> categoryIds) {
        if (categoryIds.isEmpty()) {
            return Map.of();
        }
        return categoryRepository.findAllById(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));
    }
}
