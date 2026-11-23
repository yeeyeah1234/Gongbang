package com.gongbang.chat.dto;

import java.util.List;

/** NET-05 메시지 커서 페이징 응답 (`GET /api/chat-rooms/{roomId}/messages`). */
public record ChatMessagePageResponse(List<ChatMessageResponse> items, boolean hasNext) {}
