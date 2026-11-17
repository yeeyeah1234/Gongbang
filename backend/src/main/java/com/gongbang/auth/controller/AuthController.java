package com.gongbang.auth.controller;

import com.gongbang.auth.dto.FindEmailResponse;
import com.gongbang.auth.dto.LoginRequest;
import com.gongbang.auth.dto.LoginResponse;
import com.gongbang.auth.dto.LoginResult;
import com.gongbang.auth.dto.ResetPasswordRequest;
import com.gongbang.auth.dto.RestoreRequest;
import com.gongbang.auth.dto.SendCodeRequest;
import com.gongbang.auth.dto.SendCodeResponse;
import com.gongbang.auth.dto.SignupRequest;
import com.gongbang.auth.dto.SignupResponse;
import com.gongbang.auth.dto.VerifyCodeRequest;
import com.gongbang.auth.dto.VerifyCodeResponse;
import com.gongbang.auth.service.AuthService;
import com.gongbang.auth.service.EmailVerificationService;
import com.gongbang.auth.service.RefreshCookie;
import com.gongbang.global.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** AUTH-01·02·04~07·11, MY-11 복원 */
@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;

    @PostMapping("/api/auth/email/send-code")
    public ApiResponse<SendCodeResponse> sendCode(@Valid @RequestBody SendCodeRequest request) {
        return ApiResponse.success(
                emailVerificationService.sendCode(
                        request.email(),
                        request.purpose(),
                        Boolean.TRUE.equals(request.newSignup())));
    }

    @PostMapping("/api/auth/email/verify")
    public ApiResponse<VerifyCodeResponse> verify(@Valid @RequestBody VerifyCodeRequest request) {
        return ApiResponse.success(
                new VerifyCodeResponse(
                        emailVerificationService.verify(
                                request.email(), request.purpose(), request.code())));
    }

    @PostMapping("/api/auth/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(
            @Valid @RequestBody SignupRequest request) {
        LoginResult result = authService.signup(request);
        return withRefreshCookie(
                result, new SignupResponse(result.member().getId(), result.accessToken()));
    }

    @PostMapping("/api/auth/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        LoginResult result = authService.login(request, httpRequest.getRemoteAddr());
        return withRefreshCookie(result, LoginResponse.from(result));
    }

    @PostMapping("/api/auth/token/refresh")
    public ResponseEntity<ApiResponse<Map<String, String>>> refresh(
            @CookieValue(name = RefreshCookie.NAME, required = false) String refreshToken) {
        LoginResult result = authService.refresh(refreshToken);
        return withRefreshCookie(result, Map.of("accessToken", result.accessToken()));
    }

    @PostMapping("/api/auth/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = RefreshCookie.NAME, required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, RefreshCookie.expired().toString())
                .body(ApiResponse.success());
    }

    @GetMapping("/api/auth/find-email")
    public ApiResponse<FindEmailResponse> findEmail(
            @RequestParam String nickname, HttpServletRequest httpRequest) {
        return ApiResponse.success(authService.findEmail(nickname, httpRequest.getRemoteAddr()));
    }

    @PostMapping("/api/auth/password/reset")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(
                request.email(), request.verificationToken(), request.newPassword());
        return ApiResponse.success();
    }

    @PostMapping("/api/auth/restore")
    public ResponseEntity<ApiResponse<Map<String, String>>> restore(
            @Valid @RequestBody RestoreRequest request) {
        LoginResult result = authService.restore(request.email(), request.verificationToken());
        return withRefreshCookie(result, Map.of("accessToken", result.accessToken()));
    }

    private <T> ResponseEntity<ApiResponse<T>> withRefreshCookie(LoginResult result, T body) {
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        RefreshCookie.of(
                                        result.rawRefreshToken(),
                                        result.refreshTtl(),
                                        result.rememberMe())
                                .toString())
                .body(ApiResponse.success(body));
    }
}
