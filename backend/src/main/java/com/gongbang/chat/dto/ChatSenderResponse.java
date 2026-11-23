package com.gongbang.chat.dto;

/** 채팅 메시지 발신자 요약 (`SYSTEM` 메시지는 {@code null}). */
public record ChatSenderResponse(Long memberId, String nickname, String profileImageUrl) {}
