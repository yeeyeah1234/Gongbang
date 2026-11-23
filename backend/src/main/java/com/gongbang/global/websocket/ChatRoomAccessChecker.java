package com.gongbang.global.websocket;

/** global 계층이 chat 도메인에 직접 의존하지 않도록 채팅방 접근 권한만 확인하는 포트. */
public interface ChatRoomAccessChecker {

    /** 해당 회원이 채팅방의 현재 참여자이면서 소속 팀의 활성 팀원인지 확인한다. */
    boolean canAccessRoom(Long roomId, Long memberId);
}
