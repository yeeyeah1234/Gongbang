package com.gongbang.chat.dto;

import jakarta.validation.constraints.NotNull;

/** NET-04 1:1 채팅방 열기 요청 (`POST /api/teams/{teamId}/chat-rooms/direct`). */
public record DirectChatCreateRequest(@NotNull Long memberId) {}
