package com.gongbang.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record ResetPasswordRequest(
        @NotBlank String email, @NotBlank String verificationToken, @NotBlank String newPassword) {}
