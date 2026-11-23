package com.gongbang.global.websocket;

import java.time.LocalDateTime;

/** `/sub/members/{memberId}/events` 개인 실시간 이벤트 페이로드 (API 명세서). */
public record UserEventMessage(String type, Object payload, LocalDateTime at) {}
