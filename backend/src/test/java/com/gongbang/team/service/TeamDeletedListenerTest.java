package com.gongbang.team.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.gongbang.workspace.sync.SyncServerClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TeamDeletedListenerTest {

    @Mock private SyncServerClient syncServerClient;

    @Test
    @DisplayName("팀 삭제 커밋 후 팀장의 SyncServerClient.disconnect를 호출한다")
    void 커밋후_팀장_Sync연결종료() {
        TeamDeletedListener listener = new TeamDeletedListener(syncServerClient);

        listener.onTeamDeleted(new TeamDeletedEvent(10L, 1L));

        verify(syncServerClient).disconnect(10L, 1L);
    }

    @Test
    @DisplayName("leaderId가 없는 1-인자 이벤트는 disconnect를 호출하지 않는다")
    void leaderId가_없으면_건너뛴다() {
        TeamDeletedListener listener = new TeamDeletedListener(syncServerClient);

        listener.onTeamDeleted(new TeamDeletedEvent(10L));

        verifyNoInteractions(syncServerClient);
    }
}
