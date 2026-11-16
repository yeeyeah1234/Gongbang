package com.gongbang.member.controller;

import com.gongbang.auth.domain.LoginMember;
import com.gongbang.auth.service.RefreshCookie;
import com.gongbang.global.common.ApiResponse;
import com.gongbang.member.dto.ChangePasswordRequest;
import com.gongbang.member.dto.MyProfileResponse;
import com.gongbang.member.dto.PublicProfileResponse;
import com.gongbang.member.dto.UpdateProfileRequest;
import com.gongbang.member.dto.WithdrawRequest;
import com.gongbang.member.service.AccountService;
import com.gongbang.member.service.MemberService;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** AUTH-06 닉네임 확인, MY-01·04·05·11 */
@RestController
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final AccountService accountService;

    @GetMapping("/api/members/check-nickname")
    public ApiResponse<Map<String, Boolean>> checkNickname(@RequestParam String nickname) {
        return ApiResponse.success(
                Map.of("available", memberService.isNicknameAvailable(nickname)));
    }

    @GetMapping("/api/members/me")
    public ApiResponse<MyProfileResponse> getMe(@AuthenticationPrincipal LoginMember loginMember) {
        return ApiResponse.success(memberService.getMyProfile(loginMember.getMemberId()));
    }

    @PatchMapping("/api/members/me")
    public ApiResponse<MyProfileResponse> updateMe(
            @AuthenticationPrincipal LoginMember loginMember,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.success(
                memberService.updateMyProfile(loginMember.getMemberId(), request));
    }

    @GetMapping("/api/members/{memberId}/profile")
    public ApiResponse<PublicProfileResponse> getPublicProfile(@PathVariable Long memberId) {
        return ApiResponse.success(memberService.getPublicProfile(memberId));
    }

    @PatchMapping("/api/members/me/password")
    public ApiResponse<Void> changePassword(
            @AuthenticationPrincipal LoginMember loginMember,
            @Valid @RequestBody ChangePasswordRequest request,
            @CookieValue(name = RefreshCookie.NAME, required = false) String refreshToken) {
        accountService.changePassword(
                loginMember.getMemberId(),
                request.currentPassword(),
                request.newPassword(),
                refreshToken);
        return ApiResponse.success();
    }

    @DeleteMapping("/api/members/me")
    public ResponseEntity<ApiResponse<Map<String, LocalDateTime>>> withdraw(
            @AuthenticationPrincipal LoginMember loginMember,
            @Valid @RequestBody WithdrawRequest request) {
        LocalDateTime deadline =
                accountService.withdraw(
                        loginMember.getMemberId(), request.password(), request.confirmText());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, RefreshCookie.expired().toString())
                .body(ApiResponse.success(Map.of("restoreDeadline", deadline)));
    }
}
