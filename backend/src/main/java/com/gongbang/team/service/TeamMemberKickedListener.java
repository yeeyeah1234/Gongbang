package com.gongbang.team.service;

import com.gongbang.workspace.sync.SyncServerClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 강제 퇴장 커밋 이후(`AFTER_COMMIT`) Sync 서버 문서·보드 세션을 끊는다. */
@Component
@RequiredArgsConstructor
public class TeamMemberKickedListener {

    private final SyncServerClient syncServerClient;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMemberKicked(TeamMemberKickedEvent event) {
        syncServerClient.disconnect(event.teamId(), event.memberId());
    }
}
