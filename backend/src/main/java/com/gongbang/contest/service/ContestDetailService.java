package com.gongbang.contest.service;

import com.gongbang.contest.dto.ContestDetailResponse;
import com.gongbang.contest.dto.ContestDetailView;
import com.gongbang.contest.repository.ContestRepository;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ContestDetailService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ContestRepository contestRepository;

    /**
     * MAIN-02 공모전 상세. 같은 브라우저는 하루(한국 시간 자정 기준)에 한 번만 조회수가 오른다 (⑤). 없는 공모전이면 404.
     *
     * @param viewedCookieValue 「오늘 본 공모전」 쿠키 값 (없으면 null)
     */
    @Transactional
    public ContestDetailView getContest(Long contestId, String viewedCookieValue) {
        return getContest(contestId, viewedCookieValue, ZonedDateTime.now(KST));
    }

    ContestDetailView getContest(Long contestId, String viewedCookieValue, ZonedDateTime now) {
        LinkedHashSet<Long> viewedToday = ContestViewCookie.parse(viewedCookieValue);
        boolean firstViewToday = viewedToday.add(contestId);

        if (firstViewToday && contestRepository.increaseViewCount(contestId) == 0) {
            throw new BusinessException(ErrorCode.CONTEST_NOT_FOUND);
        }
        ContestDetailResponse detail =
                contestRepository
                        .findWithCategoryById(contestId)
                        .map(contest -> ContestDetailResponse.of(contest, now.toLocalDate()))
                        .orElseThrow(() -> new BusinessException(ErrorCode.CONTEST_NOT_FOUND));
        return new ContestDetailView(detail, ContestViewCookie.build(viewedToday, now));
    }
}
