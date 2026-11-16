package com.gongbang.auth.dto;

import java.time.LocalDateTime;

/** devCode: 메일 서버(MAIL_HOST)가 없는 로컬 개발에서만 채운다. 실제 메일을 보낼 때는 항상 null */
public record SendCodeResponse(
        LocalDateTime expiresAt, LocalDateTime resendAvailableAt, String devCode) {}
