package com.gongbang.auth.dto;

/**
 * API 명세서 AUTH-10 POST /api/auth/oauth/token. 기존 회원이면 accessToken, 처음이면 signupToken.
 * loginType·email은 추가 정보 화면 표시용 (email이 null이면 직접 입력·인증, AUTH-10 ⑥)
 */
public record OAuthTokenResponse(
        String accessToken, boolean isNewMember, String signupToken, String loginType, String email) {}
