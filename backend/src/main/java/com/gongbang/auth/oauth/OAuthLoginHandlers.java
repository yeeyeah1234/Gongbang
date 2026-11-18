package com.gongbang.auth.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * AUTH-10 ③⑧: 소셜 로그인이 끝나면 프론트 /oauth/callback으로 돌려보낸다. 성공이면 1회용 키(code), 취소·오류면 error. 로그인 토큰은 여기서
 * 주지 않는다(주소에 토큰이 남지 않게).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuthLoginHandlers
        implements AuthenticationSuccessHandler, AuthenticationFailureHandler {

    private final OAuthService oAuthService;

    @Value("${app.front-base-url:http://localhost:5173}")
    private String frontBaseUrl;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
        String query =
                oAuthService.handleProviderLogin(
                        token.getAuthorizedClientRegistrationId(),
                        token.getPrincipal().getAttributes());
        clearSession(request);
        response.sendRedirect(frontBaseUrl + "/oauth/callback?" + query);
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception)
            throws IOException {
        // 사용자가 취소했거나 제공자 오류 (원인 종류만 남긴다)
        log.warn("간편 로그인 실패: {}", exception.getClass().getSimpleName());
        clearSession(request);
        response.sendRedirect(frontBaseUrl + "/oauth/callback?error=FAILED");
    }

    /** 로그인은 JWT로만 유지한다. 소셜 로그인 도중 생긴 세션은 지운다 */
    private static void clearSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }
}
