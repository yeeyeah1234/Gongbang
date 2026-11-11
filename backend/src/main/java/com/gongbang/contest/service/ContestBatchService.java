package com.gongbang.contest.service;

import com.gongbang.contest.domain.Contest;
import com.gongbang.contest.dto.ContestBatchItem;
import com.gongbang.contest.dto.ContestBatchResponse;
import com.gongbang.contest.repository.ContestRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ContestBatchService {

    private final ContestRepository contestRepository;

    @Transactional
    public ContestBatchResponse upsert(Iterable<ContestBatchItem> items) {
        int created = 0;
        int updated = 0;
        int deadlineChanged = 0;

        for (ContestBatchItem item : items) {
            Optional<Contest> existing = contestRepository.findBySourceUrl(item.sourceUrl());

            if (existing.isEmpty()) {
                contestRepository.save(Contest.create(item.sourceUrl(), item));
                created++;
                continue;
            }

            Contest contest = existing.get();
            if (!contest.getEndDate().equals(item.deadline())) {
                deadlineChanged++;
            }
            contest.update(item);
            updated++;
        }

        return new ContestBatchResponse(created, updated, deadlineChanged);
    }
}
