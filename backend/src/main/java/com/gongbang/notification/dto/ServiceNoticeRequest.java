package com.gongbang.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** NOTI-07: 제목 1~50자, 내용 1~2,000자 (DB 명세서) */
public record ServiceNoticeRequest(
        @NotBlank @Size(max = 50) String title,
        @NotBlank @Size(max = 2000) String content,
        boolean policyChange,
        boolean sendEmail) {
    public ServiceNoticeRequest(String title, String content) {
        this(title, content, false, false);
    }
}
