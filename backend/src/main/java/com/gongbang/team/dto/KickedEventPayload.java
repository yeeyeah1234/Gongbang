package com.gongbang.team.dto;

/** 개인 이벤트(`/sub/members/{memberId}/events`, `type = KICKED`) 페이로드. */
public record KickedEventPayload(Long teamId, String teamName) {}
