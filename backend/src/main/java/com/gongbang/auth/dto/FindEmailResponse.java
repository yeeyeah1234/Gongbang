package com.gongbang.auth.dto;

import java.time.LocalDateTime;

/** AUTH-02 ①. 가입일은 Figma 02 화면 표시용 */
public record FindEmailResponse(String maskedEmail, String loginType, LocalDateTime joinedAt) {}
