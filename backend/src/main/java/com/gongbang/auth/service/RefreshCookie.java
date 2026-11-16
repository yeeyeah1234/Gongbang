package com.gongbang.auth.service;

import java.time.Duration;
import org.springframework.http.ResponseCookie;

/** refresh 토큰 httpOnly 쿠키 (AGENTS 2-1). 경로를 /api/auth로 좁혀 다른 API에는 붙지 않게 한다. */
public final class RefreshCookie {

    public static final String NAME = "refresh_token";
    private static final String PATH = "/api/auth";

    private RefreshCookie() {}

    /** rememberMe가 아니면 브라우저를 닫을 때 사라지는 쿠키(maxAge 없음) */
    public static ResponseCookie of(String token, Duration maxAge, boolean rememberMe) {
        ResponseCookie.ResponseCookieBuilder builder =
                ResponseCookie.from(NAME, token).httpOnly(true).sameSite("Lax").path(PATH);
        // TODO(확인 필요): 배포가 HTTPS로 정해지면 secure(true)
        if (rememberMe) {
            builder.maxAge(maxAge);
        }
        return builder.build();
    }

    public static ResponseCookie expired() {
        return ResponseCookie.from(NAME, "")
                .httpOnly(true)
                .sameSite("Lax")
                .path(PATH)
                .maxAge(0)
                .build();
    }
}
