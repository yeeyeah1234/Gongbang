package com.gongbang.chat.service;

import com.gongbang.chat.domain.ChatMessage;

/** 채팅 메시지 저장 후 커밋 완료 뒤 STOMP 브로드캐스트를 트리거하는 내부 도메인 이벤트. */
public record ChatMessageSentEvent(Long teamId, Long roomId, ChatMessage message) {}
