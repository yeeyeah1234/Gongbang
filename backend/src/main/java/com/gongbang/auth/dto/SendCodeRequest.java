package com.gongbang.auth.dto;

import com.gongbang.auth.domain.VerificationPurpose;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * newSignup: 탈퇴 신청한 이메일에서 [새로 가입하기]를 고른 경우 (AUTH-05 ⑦). TODO(확인 필요): API 명세서 요청 필드에 newSignup 추가
 */
public record SendCodeRequest(
        @NotBlank @Email String email, @NotNull VerificationPurpose purpose, Boolean newSignup) {}
