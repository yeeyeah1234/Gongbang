package com.gongbang.workspace.notice.controller;

import com.gongbang.auth.domain.LoginMember;
import com.gongbang.global.common.ApiResponse;
import com.gongbang.global.common.PageResponse;
import com.gongbang.workspace.notice.dto.NoticeCreateRequest;
import com.gongbang.workspace.notice.dto.NoticeDetailResponse;
import com.gongbang.workspace.notice.dto.NoticeListItemResponse;
import com.gongbang.workspace.notice.dto.NoticeUnreadCountResponse;
import com.gongbang.workspace.notice.dto.NoticeUpdateRequest;
import com.gongbang.workspace.notice.service.NoticeQueryService;
import com.gongbang.workspace.notice.service.NoticeService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 팀 공지 API (NTC-01~05). 명세서의 /rooms/{roomId} 대신 /teams/{teamId}로 통일했다 (CAL-06과 같은 기준). 값 검증과 권한 확인은
 * service가 한다 (조회 = 팀원, 작성·수정·삭제·고정 = 팀장).
 */
@RestController
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeService noticeService;
    private final NoticeQueryService noticeQueryService;

    /** NTC-01 목록: 고정 맨 위 → 최신순. */
    @GetMapping("/api/teams/{teamId}/notices")
    public ApiResponse<PageResponse<NoticeListItemResponse>> getNotices(
            @AuthenticationPrincipal LoginMember me,
            @PathVariable Long teamId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(
                noticeQueryService.getNotices(me.getMemberId(), teamId, page, size));
    }

    /** NTC-05 고정 배너. 없으면 data null. */
    @GetMapping("/api/teams/{teamId}/notices/pinned")
    public ApiResponse<NoticeDetailResponse> getPinned(
            @AuthenticationPrincipal LoginMember me, @PathVariable Long teamId) {
        return ApiResponse.success(noticeQueryService.getPinned(me.getMemberId(), teamId));
    }

    /** 공지 탭 배지 (명세서에 없어 추가). */
    @GetMapping("/api/teams/{teamId}/notices/unread-count")
    public ApiResponse<NoticeUnreadCountResponse> getUnreadCount(
            @AuthenticationPrincipal LoginMember me, @PathVariable Long teamId) {
        return ApiResponse.success(noticeQueryService.getUnreadCount(me.getMemberId(), teamId));
    }

    /** NTC-01 작성. */
    @PostMapping("/api/teams/{teamId}/notices")
    public ApiResponse<Map<String, Long>> create(
            @AuthenticationPrincipal LoginMember me,
            @PathVariable Long teamId,
            @RequestBody NoticeCreateRequest request) {
        return ApiResponse.success(
                Map.of("noticeId", noticeService.create(me.getMemberId(), teamId, request)));
    }

    /** NTC-02 상세 (읽음 기록을 남긴다). */
    @GetMapping("/api/notices/{noticeId}")
    public ApiResponse<NoticeDetailResponse> getNotice(
            @AuthenticationPrincipal LoginMember me, @PathVariable Long noticeId) {
        return ApiResponse.success(noticeQueryService.getNotice(me.getMemberId(), noticeId));
    }

    /** NTC-04 수정. */
    @PatchMapping("/api/notices/{noticeId}")
    public ApiResponse<Map<String, Long>> update(
            @AuthenticationPrincipal LoginMember me,
            @PathVariable Long noticeId,
            @RequestBody NoticeUpdateRequest request) {
        return ApiResponse.success(
                Map.of("noticeId", noticeService.update(me.getMemberId(), noticeId, request)));
    }

    /** NTC-03 삭제. */
    @DeleteMapping("/api/notices/{noticeId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal LoginMember me, @PathVariable Long noticeId) {
        noticeService.delete(me.getMemberId(), noticeId);
        return ApiResponse.success();
    }

    /** NTC-05 고정 (이미 고정이면 그대로 성공). */
    @PutMapping("/api/notices/{noticeId}/pin")
    public ApiResponse<Void> pin(
            @AuthenticationPrincipal LoginMember me, @PathVariable Long noticeId) {
        noticeService.pin(me.getMemberId(), noticeId);
        return ApiResponse.success();
    }

    /** NTC-05 고정 해제 (이미 풀려 있으면 그대로 성공). */
    @DeleteMapping("/api/notices/{noticeId}/pin")
    public ApiResponse<Void> unpin(
            @AuthenticationPrincipal LoginMember me, @PathVariable Long noticeId) {
        noticeService.unpin(me.getMemberId(), noticeId);
        return ApiResponse.success();
    }
}
