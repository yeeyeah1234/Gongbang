package com.gongbang.contest.service;

import com.gongbang.contest.domain.Contest;
import com.gongbang.contest.dto.ContestCardResponse;
import com.gongbang.contest.repository.ContestRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다른 도메인이 공모전 id 목록을 카드 모양으로 받을 때 쓴다 (MY-03 북마크 목록, 정진우 추가). 다른 도메인이 ContestRepository를 직접 부르지 않게
 * 만든 창구다 (AGENTS 3). 기존 클래스는 고치지 않았다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContestCardService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ContestRepository contestRepository;

    /** ids 순서를 그대로 지킨다. 없는 id는 빠진다. */
    public List<ContestCardResponse> getCardsByIds(List<Long> ids, boolean isBookmarked) {
        if (ids.isEmpty()) {
            return List.of();
        }
        LocalDate today = LocalDate.now(KST);
        Map<Long, Contest> byId =
                contestRepository.findAllById(ids).stream()
                        .collect(Collectors.toMap(Contest::getId, Function.identity()));
        return ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(contest -> withBookmark(ContestCardResponse.of(contest, today), isBookmarked))
                .toList();
    }

    private static ContestCardResponse withBookmark(
            ContestCardResponse card, boolean isBookmarked) {
        return new ContestCardResponse(
                card.contestId(),
                card.title(),
                card.host(),
                card.categoryCode(),
                card.categoryName(),
                card.thumbnailUrl(),
                card.deadline(),
                card.dDay(),
                card.isClosed(),
                isBookmarked);
    }
}
