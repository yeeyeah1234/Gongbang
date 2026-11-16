package com.gongbang.auth.dto;

import java.time.LocalDateTime;

public record SendCodeResponse(LocalDateTime expiresAt, LocalDateTime resendAvailableAt) {}
