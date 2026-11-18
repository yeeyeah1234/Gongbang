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
            Category category =
                    categoryRepository
                            .findByCode(item.categoryCode())
                            .orElseThrow(
                                    () ->
                                            new BusinessException(
                                                    ErrorCode.INVALID_INPUT,
                                                    "존재하지 않는 categoryCode입니다: "
                                                            + item.categoryCode()));

            Optional<Contest> existing = contestRepository.findBySourceUrl(item.sourceUrl());

            if (existing.isEmpty()) {
                contestRepository.save(Contest.create(item.sourceUrl(), category, item));
                created++;
                continue;
            }

            Contest contest = existing.get();
            if (!contest.getEndDate().equals(item.deadline())) {
                deadlineChanged++;
                // TODO(확인 필요): CalendarService.upsertAutoEvent(teamId) 호출 (API 명세서 CAL-06, CRW-01
                // ⑦).
                // 마감일이 바뀌면 이 공모전에 연결된 팀마다 자동 일정·예약 알림을 갱신한다. 이상현이 만들고 연결하기로 함 (11/13)
            }
            contest.update(category, item);
            updated++;
        }

        return new ContestBatchResponse(created, updated, deadlineChanged);
    }
}
