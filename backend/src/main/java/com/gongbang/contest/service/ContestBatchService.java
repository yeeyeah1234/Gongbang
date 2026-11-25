package com.gongbang.contest.service;

import com.gongbang.contest.domain.Category;
import com.gongbang.contest.domain.Contest;
import com.gongbang.contest.domain.ContestDeadlineChangedEvent;
import com.gongbang.contest.dto.ContestBatchItem;
import com.gongbang.contest.dto.ContestBatchResponse;
import com.gongbang.contest.repository.CategoryRepository;
import com.gongbang.contest.repository.ContestRepository;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.global.validation.UrlPolicy;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ContestBatchService {

    private final ContestRepository contestRepository;
    private final CategoryRepository categoryRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ContestBatchResponse upsert(Iterable<ContestBatchItem> items) {
        int created = 0;
        int updated = 0;
        int deadlineChanged = 0;

        for (ContestBatchItem raw : items) {
            // #120 공통 URL 정책: 대소문자 무관 http(s) + 호스트 필수, 빈 값은 null. 어기면 INVALID_INPUT
            ContestBatchItem item =
                    raw.withHomepageUrl(UrlPolicy.validateOrNull(raw.homepageUrl()));
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
            boolean deadlineMoved = !contest.getEndDate().equals(item.deadline());
            contest.update(category, item);
            if (deadlineMoved) {
                deadlineChanged++;
                // CRW-01 ⑦, CAL-06: 연결된 팀의 자동 일정·예약 알림 갱신은 캘린더(이상현)가 커밋 후에 받아서 처리한다
                eventPublisher.publishEvent(
                        new ContestDeadlineChangedEvent(contest.getId(), contest.getEndDate()));
            }
            updated++;
        }

        return new ContestBatchResponse(created, updated, deadlineChanged);
    }
}
