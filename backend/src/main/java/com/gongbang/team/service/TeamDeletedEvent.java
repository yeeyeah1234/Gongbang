package com.gongbang.team.service;

/** WS-08 ⑨ 팀 삭제 트랜잭션 커밋 뒤 Sync 서버 연결 종료 및 알림 정리용 내부 이벤트. */
public record TeamDeletedEvent(Long teamId, Long leaderId) {

    public TeamDeletedEvent(Long teamId) {
        this(teamId, null);
    }
}
