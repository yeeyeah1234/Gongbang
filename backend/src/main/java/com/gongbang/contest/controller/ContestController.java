package com.gongbang.contest.controller;

import com.gongbang.auth.domain.LoginMember;
import com.gongbang.contest.dto.CategoryResponse;
import com.gongbang.contest.dto.ContestCardResponse;
import com.gongbang.contest.dto.ContestDetailResponse;
import com.gongbang.contest.dto.ContestDetailView;
import com.gongbang.contest.service.ContestDetailService;
import com.gongbang.contest.service.ContestQueryService;
import com.gongbang.contest.service.ContestViewCookie;
import com.gongbang.global.common.ApiResponse;
import com.gongbang.global.common.PageResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 공모전 공개 API (비회원도 조회 가능). 로그인 회원이면 북마크 여부를 함께 채운다 (MAIN-06 ③·MAIN-02 ④). */
@RestController
@RequiredArgsConstructor
public class ContestController {

    private final ContestQueryService contestQueryService;
    private final ContestDetailService contestDetailService;

    /** MAIN-01·04·05: 기본은 진행 중 공모전, 최신순, 20개씩. */
    @GetMapping("/api/contests")
    public ApiResponse<PageResponse<ContestCardResponse>> getContests(
            @RequestParam(defaultValue = "active") String status,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal LoginMember me) {
        return ApiResponse.success(
                contestQueryService.getContests(
                        status, sort, category, keyword, page, size, memberIdOf(me)));
    }

    /** SRCH-04 인기 공모전(추천): 진행 중, 조회수 순. */
    @GetMapping("/api/contests/popular")
    public ApiResponse<List<ContestCardResponse>> getPopularContests(
            @RequestParam(defaultValue = "5") int size, @AuthenticationPrincipal LoginMember me) {
        return ApiResponse.success(contestQueryService.getPopularContests(size, memberIdOf(me)));
    }

    /** MAIN-02: 같은 브라우저는 하루(한국 시간 자정 기준)에 한 번만 조회수 +1. 「오늘 본 공모전」 쿠키를 함께 내려준다. */
    @GetMapping("/api/contests/{contestId}")
    public ResponseEntity<ApiResponse<ContestDetailResponse>> getContest(
            @PathVariable Long contestId,
            @CookieValue(name = ContestViewCookie.NAME, required = false) String viewedCookie,
            @AuthenticationPrincipal LoginMember me) {
        ContestDetailView view =
                contestDetailService.getContest(contestId, viewedCookie, memberIdOf(me));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, view.viewedCookie().toString())
                .body(ApiResponse.success(view.detail()));
    }

    /** MAIN-05 */
    @GetMapping("/api/categories")
    public ApiResponse<List<CategoryResponse>> getCategories() {
        return ApiResponse.success(contestQueryService.getCategories());
    }

    /** 비로그인이면 null */
    private static Long memberIdOf(LoginMember me) {
        return me == null ? null : me.getMemberId();
    }
}
