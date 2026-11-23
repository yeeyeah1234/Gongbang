package com.gongbang.chat.dto;

import com.gongbang.chat.domain.ChatMessageType;
import java.time.LocalDateTime;

/** NET-05 채팅 메시지 단건 응답 (목록 조회 및 STOMP 브로드캐스트 공용). */
public record ChatMessageResponse(
        Long messageId,
        ChatMessageType type,
        ChatSenderResponse sender,
        String content,
        String imageUrl,
        LocalDateTime createdAt) {}
