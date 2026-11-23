package com.gongbang.chat.dto;

import com.gongbang.chat.domain.ChatRoomType;
import java.time.LocalDateTime;

/** NET-04 내 채팅방 목록 항목 응답 (`GET /api/teams/{teamId}/chat-rooms`). */
public record ChatRoomResponse(
        Long roomId,
        ChatRoomType type,
        String name,
        PartnerInfo partner,
        LastMessageInfo lastMessage,
        long unreadCount) {

    /** 1:1 채팅 상대방 정보 (`TEAM` 방은 {@code null}). */
    public record PartnerInfo(Long memberId, String nickname, String profileImageUrl) {}

    /** 마지막 메시지 요약 (메시지가 없으면 {@code null}). */
    public record LastMessageInfo(String content, String senderNickname, LocalDateTime createdAt) {}
}
