package com.gongbang.team.service;

import com.gongbang.workspace.sync.SyncServerClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** WS-09 팀 나가기 커밋 이후(`AFTER_COMMIT`) Sync 서버 문서·보드·접속자 세션을 끊는다. */
@Component
@RequiredArgsConstructor
public class TeamMemberLeftListener {

    private final SyncServerClient syncServerClient;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMemberLeft(TeamMemberLeftEvent event) {
        syncServerClient.disconnect(event.teamId(), event.memberId());
    }
}
