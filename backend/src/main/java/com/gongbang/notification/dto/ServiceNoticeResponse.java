package com.gongbang.notification.dto;

import com.gongbang.notification.domain.ServiceNotice;
import java.time.LocalDateTime;

public record ServiceNoticeResponse(
        Long serviceNoticeId, String title, String content, LocalDateTime createdAt) {

    public static ServiceNoticeResponse from(ServiceNotice n) {
        return new ServiceNoticeResponse(n.getId(), n.getTitle(), n.getContent(), n.getCreatedAt());
    }
}
