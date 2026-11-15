package com.gongbang.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record RestoreRequest(@NotBlank String email, @NotBlank String verificationToken) {}
