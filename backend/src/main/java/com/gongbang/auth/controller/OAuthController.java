package com.gongbang.auth.controller;

import com.gongbang.auth.dto.LoginResult;
import com.gongbang.auth.dto.OAuthSignupRequest;
import com.gongbang.auth.dto.OAuthTokenRequest;
import com.gongbang.auth.dto.OAuthTokenResponse;
import com.gongbang.auth.dto.SignupResponse;
import com.gongbang.auth.oauth.OAuthClientConfig.EnabledOAuthProviders;
import com.gongbang.auth.oauth.OAuthService;
import com.gongbang.auth.oauth.OAuthService.OAuthExchange;
import com.gongbang.auth.service.RefreshCookie;
import com.gongbang.global.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** AUTH-10 간편 로그인. 시작은 GET /oauth2/authorization/{kakao|google} (Spring Security가 처리) */
@RestController
@RequiredArgsConstructor
public class OAuthController {

    private final OAuthService oAuthService;
    private final EnabledOAuthProviders enabledProviders;

    /** 키가 설정돼 켜진 제공자 (화면이 버튼을 살릴지 정한다). TODO(확인 필요): API 명세서 행 추가 */
    @GetMapping("/api/auth/oauth/providers")
    public ApiResponse<Map<String, List<String>>> providers() {
        return ApiResponse.success(Map.of("providers", enabledProviders.names()));
    }

    @PostMapping("/api/auth/oauth/token")
    public ResponseEntity<ApiResponse<OAuthTokenResponse>> token(
            @Valid @RequestBody OAuthTokenRequest request) {
        OAuthExchange exchange = oAuthService.exchange(request.code());
        if (exchange.isNewMember()) {
            return ResponseEntity.ok(
                    ApiResponse.success(
                            new OAuthTokenResponse(
                                    null,
                                    true,
                                    exchange.signupToken(),
                                    exchange.loginType().name(),
                                    exchange.email())));
        }
        LoginResult result = exchange.loginResult();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(result))
                .body(
                        ApiResponse.success(
                                new OAuthTokenResponse(result.accessToken(), false, null, null, null)));
    }

    @PostMapping("/api/auth/oauth/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(
            @Valid @RequestBody OAuthSignupRequest request) {
        LoginResult result = oAuthService.signup(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(result))
                .body(
                        ApiResponse.success(
                                new SignupResponse(result.member().getId(), result.accessToken())));
    }

    private static String refreshCookie(LoginResult result) {
        return RefreshCookie.of(result.rawRefreshToken(), result.refreshTtl(), result.rememberMe())
                .toString();
    }
}
