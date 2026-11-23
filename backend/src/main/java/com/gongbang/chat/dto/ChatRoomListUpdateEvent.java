package com.gongbang.chat.dto;

import java.time.LocalDateTime;

/** 채팅방 목록 및 안 읽음 수 실시간 갱신 이벤트 (`/sub/teams/{teamId}/members/{memberId}/chat-rooms`). */
public record ChatRoomListUpdateEvent(
        Long teamId,
        Long roomId,
        ChatRoomResponse.LastMessageInfo lastMessage,
        long unreadCount,
        LocalDateTime updatedAt) {}
