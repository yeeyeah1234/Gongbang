package com.gongbang.notification.controller;

import com.gongbang.auth.domain.LoginMember;
import com.gongbang.global.common.ApiResponse;
import com.gongbang.global.common.PageResponse;
import com.gongbang.notification.dto.NotificationResponse;
import com.gongbang.notification.dto.NotificationSettingItem;
import com.gongbang.notification.dto.NotificationSettingsRequest;
import com.gongbang.notification.service.NotificationQueryService;
import com.gongbang.notification.service.NotificationSettingService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** NOTI-01·02·03, MY-10 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationQueryService queryService;
    private final NotificationSettingService settingService;

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> unreadCount(@AuthenticationPrincipal LoginMember me) {
        return ApiResponse.success(Map.of("count", queryService.unreadCount(me.getMemberId())));
    }

    @GetMapping
    public ApiResponse<PageResponse<NotificationResponse>> list(
            @AuthenticationPrincipal LoginMember me,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(required = false) String category) {
        return ApiResponse.success(
                queryService.list(me.getMemberId(), page, size, unreadOnly, category));
    }

    @PatchMapping("/{notificationId}/read")
    public ApiResponse<Void> read(
            @AuthenticationPrincipal LoginMember me, @PathVariable Long notificationId) {
        queryService.markRead(me.getMemberId(), notificationId);
        return ApiResponse.success();
    }

    @PatchMapping("/read-all")
    public ApiResponse<Void> readAll(@AuthenticationPrincipal LoginMember me) {
        queryService.markAllRead(me.getMemberId());
        return ApiResponse.success();
    }

    /** NOTI-03 ⑤ 메일 링크로 연 화면의 알림 읽음 (명세서에 없어 추가 — TODO(확인 필요): API 명세서 행 추가) */
    @PatchMapping("/read-by-link")
    public ApiResponse<Void> readByLink(
            @AuthenticationPrincipal LoginMember me, @RequestParam String linkUrl) {
        queryService.markReadByLink(me.getMemberId(), linkUrl);
        return ApiResponse.success();
    }

    @DeleteMapping("/{notificationId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal LoginMember me, @PathVariable Long notificationId) {
        queryService.delete(me.getMemberId(), notificationId);
        return ApiResponse.success();
    }

    /** 읽은 알림 삭제 */
    @DeleteMapping("/read")
    public ApiResponse<Void> deleteRead(@AuthenticationPrincipal LoginMember me) {
        queryService.deleteRead(me.getMemberId());
        return ApiResponse.success();
    }

    @GetMapping("/settings")
    public ApiResponse<Map<String, List<NotificationSettingItem>>> settings(
            @AuthenticationPrincipal LoginMember me) {
        return ApiResponse.success(Map.of("settings", settingService.get(me.getMemberId())));
    }

    @PutMapping("/settings")
    public ApiResponse<Map<String, List<NotificationSettingItem>>> updateSettings(
            @AuthenticationPrincipal LoginMember me,
            @Valid @RequestBody NotificationSettingsRequest request) {
        return ApiResponse.success(
                Map.of("settings", settingService.update(me.getMemberId(), request.settings())));
    }
}
