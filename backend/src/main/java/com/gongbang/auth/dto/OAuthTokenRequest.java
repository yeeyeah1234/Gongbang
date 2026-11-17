package com.gongbang.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record OAuthTokenRequest(@NotBlank String code) {}
