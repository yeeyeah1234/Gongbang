package com.gongbang.team.service;

import static org.mockito.Mockito.verify;

import com.gongbang.workspace.sync.SyncServerClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TeamMemberLeftListenerTest {

    @Mock private SyncServerClient syncServerClient;

    @Test
    @DisplayName("팀 나가기 커밋 후 SyncServerClient.disconnect를 호출한다")
    void 커밋후_Sync연결종료() {
        TeamMemberLeftListener listener = new TeamMemberLeftListener(syncServerClient);

        listener.onMemberLeft(new TeamMemberLeftEvent(10L, 25L));

        verify(syncServerClient).disconnect(10L, 25L);
    }
}
