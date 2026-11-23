package com.gongbang.chat.dto;

import jakarta.validation.constraints.NotNull;

/** NET-05 읽음 처리 요청 (`PATCH /api/chat-rooms/{roomId}/read`). */
public record ChatMessageReadRequest(@NotNull Long lastReadMessageId) {}
