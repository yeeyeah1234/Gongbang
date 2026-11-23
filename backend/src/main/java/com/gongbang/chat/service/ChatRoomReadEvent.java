package com.gongbang.chat.service;

import com.gongbang.chat.dto.ChatRoomResponse;
import java.time.LocalDateTime;

/** 읽음 처리 후 커밋 완료 뒤 채팅방 목록 안읽음 수를 갱신하는 내부 도메인 이벤트. */
public record ChatRoomReadEvent(
        Long teamId,
        Long roomId,
        Long memberId,
        ChatRoomResponse.LastMessageInfo lastMessage,
        LocalDateTime updatedAt) {}
