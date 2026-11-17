package com.gongbang.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * API 명세서 AUTH-01. extraVerificationToken은 5번 실패 뒤 추가 인증(purpose=LOGIN)을 통과하고 받은 값. TODO(확인 필요):
 * API 명세서 요청 필드에 extraVerificationToken 추가 요청 (추가 인증 결과를 넘길 칸이 없음)
 */
public record LoginRequest(
        @NotBlank String email,
        @NotBlank String password,
        boolean rememberMe,
        String extraVerificationToken) {}
