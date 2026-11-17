package com.gongbang.auth.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** API 명세서 AUTH-10 POST /api/auth/oauth/signup. email·verificationToken은 제공자가 이메일을 주지 않았을 때만 */
public record OAuthSignupRequest(
        @NotBlank String signupToken,
        @NotBlank String nickname,
        @NotEmpty List<@Valid TermsItem> terms,
        @NotEmpty List<String> interestCategoryCodes,
        String email,
        String verificationToken) {}
