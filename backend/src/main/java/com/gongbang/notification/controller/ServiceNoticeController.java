package com.gongbang.notification.controller;

import com.gongbang.auth.domain.LoginMember;
import com.gongbang.global.common.ApiResponse;
import com.gongbang.notification.dto.ServiceNoticeRequest;
import com.gongbang.notification.dto.ServiceNoticeResponse;
import com.gongbang.notification.service.ServiceNoticeService;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** NOTI-07 운영 공지 (작성은 운영자만) */
@RestController
@RequiredArgsConstructor
public class ServiceNoticeController {

    private final ServiceNoticeService service;

    @GetMapping("/api/service-notices/{serviceNoticeId}")
    public ApiResponse<ServiceNoticeResponse> get(@PathVariable Long serviceNoticeId) {
        return ApiResponse.success(service.get(serviceNoticeId));
    }

    @PostMapping("/api/admin/service-notices")
    public ApiResponse<Map<String, Long>> create(
            @AuthenticationPrincipal LoginMember me,
            @Valid @RequestBody ServiceNoticeRequest request) {
        return ApiResponse.success(Map.of("serviceNoticeId", service.create(me, request)));
    }

    @PatchMapping("/api/admin/service-notices/{serviceNoticeId}")
    public ApiResponse<Map<String, Long>> update(
            @AuthenticationPrincipal LoginMember me,
            @PathVariable Long serviceNoticeId,
            @Valid @RequestBody ServiceNoticeRequest request) {
        return ApiResponse.success(
                Map.of("serviceNoticeId", service.update(me, serviceNoticeId, request)));
    }

    @DeleteMapping("/api/admin/service-notices/{serviceNoticeId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal LoginMember me, @PathVariable Long serviceNoticeId) {
        service.delete(me, serviceNoticeId);
        return ApiResponse.success();
    }
}
