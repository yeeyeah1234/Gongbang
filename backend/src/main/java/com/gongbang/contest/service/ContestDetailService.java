package com.gongbang.contest.service;

import com.gongbang.contest.dto.ContestDetailResponse;
import com.gongbang.contest.dto.ContestDetailView;
import com.gongbang.contest.repository.ContestRepository;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.service.BookmarkService;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ContestDetailService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ContestRepository contestRepository;
    // MAIN-02 ④: 북마크는 정진우 member 도메인 service로 확인한다 (AGENTS 3)
    private final BookmarkService bookmarkService;

    /**
     * MAIN-02 공모전 상세. 같은 브라우저는 하루(한국 시간 자정 기준)에 한 번만 조회수가 오른다 (⑤). 없는 공모전이면 404.
     *
     * <p>TODO(확인 필요): 인증 모듈(정진우)이 생기면 로그인 회원은 회원 ID 기준으로 하루 한 번만 센다 (쿠키를 지워도 다시
     * 오르지 않게). 비로그인 사용자는 지금처럼 쿠키 기준으로 계속 센다 — 비회원 조회수도 반드시 쌓여야 한다.
     *
     * @param viewedCookieValue 「오늘 본 공모전」 쿠키 값 (없으면 null)
     * @param memberId 로그인 회원 id (비로그인이면 null, 북마크 표시에 쓴다)
     */
    @Transactional
    public ContestDetailView getContest(Long contestId, String viewedCookieValue, Long memberId) {
        return getContest(contestId, viewedCookieValue, memberId, ZonedDateTime.now(KST));
    }

    ContestDetailView getContest(
            Long contestId, String viewedCookieValue, Long memberId, ZonedDateTime now) {
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
        boolean bookmarked =
                bookmarkService
                        .findBookmarkedContestIds(memberId, List.of(contestId))
                        .contains(contestId);
        return new ContestDetailView(
                detail.withBookmark(bookmarked), ContestViewCookie.build(viewedToday, now));
    }
}
