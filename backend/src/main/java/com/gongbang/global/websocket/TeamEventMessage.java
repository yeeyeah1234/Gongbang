package com.gongbang.global.websocket;

import java.time.LocalDateTime;

/** `/sub/teams/{teamId}/events` 팀 실시간 이벤트 페이로드 (API 명세서). */
public record TeamEventMessage(
        String type, Long teamId, Long targetId, Long actorId, LocalDateTime at) {}
