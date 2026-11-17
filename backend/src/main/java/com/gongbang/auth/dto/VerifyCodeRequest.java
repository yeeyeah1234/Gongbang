package com.gongbang.auth.dto;

import com.gongbang.auth.domain.VerificationPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record VerifyCodeRequest(
        @NotBlank String email, @NotNull VerificationPurpose purpose, @NotBlank String code) {}
