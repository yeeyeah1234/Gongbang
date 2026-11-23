package com.gongbang.chat.dto;

import com.gongbang.chat.domain.ChatMessageType;

/** NET-05 STOMP 메시지 전송 요청 (`/pub/chat-rooms/{roomId}/send`). */
public record ChatSendRequest(ChatMessageType type, String content, Long fileId) {}
