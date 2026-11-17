package com.gongbang.member.controller;

import com.gongbang.auth.domain.LoginMember;
import com.gongbang.contest.dto.ContestCardResponse;
import com.gongbang.global.common.ApiResponse;
import com.gongbang.global.common.PageResponse;
import com.gongbang.member.service.BookmarkService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** MY-03 북마크 */
@RestController
@RequiredArgsConstructor
public class BookmarkController {

    private final BookmarkService bookmarkService;

    @GetMapping("/api/members/me/bookmarks")
    public ApiResponse<PageResponse<ContestCardResponse>> bookmarks(
            @AuthenticationPrincipal LoginMember me,
            @RequestParam(defaultValue = "deadline") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(
                bookmarkService.getBookmarks(me.getMemberId(), sort, page, size));
    }

    @PutMapping("/api/contests/{contestId}/bookmark")
    public ApiResponse<Map<String, Boolean>> add(
            @AuthenticationPrincipal LoginMember me, @PathVariable Long contestId) {
        return ApiResponse.success(
                Map.of(
                        "isBookmarked",
                        bookmarkService.setBookmark(me.getMemberId(), contestId, true)));
    }

    @DeleteMapping("/api/contests/{contestId}/bookmark")
    public ApiResponse<Map<String, Boolean>> remove(
            @AuthenticationPrincipal LoginMember me, @PathVariable Long contestId) {
        return ApiResponse.success(
                Map.of(
                        "isBookmarked",
                        bookmarkService.setBookmark(me.getMemberId(), contestId, false)));
    }
}
