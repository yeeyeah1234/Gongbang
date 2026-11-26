package com.gongbang.team.service;

/** WS-09 팀 나가기 트랜잭션 커밋 뒤 Sync 서버 연결 종료를 실행하기 위한 내부 이벤트. */
public record TeamMemberLeftEvent(Long teamId, Long memberId) {}
