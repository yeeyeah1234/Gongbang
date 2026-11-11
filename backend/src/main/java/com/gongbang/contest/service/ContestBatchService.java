package com.gongbang.contest.service;

import com.gongbang.contest.domain.Category;
import com.gongbang.contest.domain.Contest;
import com.gongbang.contest.dto.ContestBatchItem;
import com.gongbang.contest.dto.ContestBatchResponse;
import com.gongbang.contest.repository.CategoryRepository;
import com.gongbang.contest.repository.ContestRepository;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ContestBatchService {

    private final ContestRepository contestRepository;
    private final CategoryRepository categoryRepository;

    @Transactional
    public ContestBatchResponse upsert(Iterable<ContestBatchItem> items) {
        int created = 0;
        int updated = 0;
        int deadlineChanged = 0;

        for (ContestBatchItem item : items) {
            Category category = categoryRepository
                    .findByCode(item.categoryCode())
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.INVALID_INPUT, "존재하지 않는 categoryCode입니다: " + item.categoryCode()));

            Optional<Contest> existing = contestRepository.findBySourceUrl(item.sourceUrl());

            if (existing.isEmpty()) {
                contestRepository.save(Contest.create(item.sourceUrl(), category, item));
                created++;
                continue;
            }

            Contest contest = existing.get();
            if (!contest.getEndDate().equals(item.deadline())) {
                deadlineChanged++;
                // TODO(확인 필요): API 명세서 CRW-01 비고 - 마감일이 바뀌면 자동 일정(CAL-06 ④)·
                // 예약 알림을 재예약해야 함. team/calendar 도메인(이상현)이 아직 없어서 지금은 호출하지 않음.
            }
            contest.update(category, item);
            updated++;
        }

        return new ContestBatchResponse(created, updated, deadlineChanged);
    }
}
