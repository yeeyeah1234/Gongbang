package com.gongbang.team.service;

/** 강제 퇴장 트랜잭션 커밋 뒤 Sync 서버 연결 종료를 실행하기 위한 내부 이벤트. */
public record TeamMemberKickedEvent(Long teamId, Long memberId) {}
