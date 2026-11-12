package com.gongbang.contest.service;

import com.gongbang.contest.dto.ContestDetailResponse;
import com.gongbang.contest.repository.ContestRepository;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ContestDetailService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ContestRepository contestRepository;

    /** MAIN-02: 상세를 열 때마다 조회수가 1 오른다 (⑤). 없는 공모전이면 404. */
    @Transactional
    public ContestDetailResponse getContest(Long contestId) {
        if (contestRepository.increaseViewCount(contestId) == 0) {
            throw new BusinessException(ErrorCode.CONTEST_NOT_FOUND);
        }
        return contestRepository
                .findWithCategoryById(contestId)
                .map(contest -> ContestDetailResponse.of(contest, LocalDate.now(KST)))
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTEST_NOT_FOUND));
    }
}
