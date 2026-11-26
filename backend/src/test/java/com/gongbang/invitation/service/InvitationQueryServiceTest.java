package com.gongbang.invitation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.gongbang.invitation.domain.TeamInvitation;
import com.gongbang.invitation.dto.RecruitingPostResponse;
import com.gongbang.invitation.repository.TeamInvitationRepository;
import com.gongbang.post.domain.RecruitPost;
import com.gongbang.post.service.PostQueryService;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InvitationQueryServiceTest {

    @Mock private TeamInvitationRepository teamInvitationRepository;
    @Mock private PostQueryService postQueryService;
    @InjectMocks private InvitationQueryService queryService;

    @Test
    @DisplayName("findStatuses는 여러 초대 ID의 상태를 한 번에 맵으로 돌려주고 빈 입력이면 DB를 조회하지 않는다")
    void 초대상태_일괄조회() {
        assertThat(queryService.findStatuses(null)).isEmpty();
        assertThat(queryService.findStatuses(List.of())).isEmpty();
        assertThat(queryService.findStatuses(Arrays.asList((Long) null))).isEmpty();
        verifyNoInteractions(teamInvitationRepository);

        LocalDateTime now = LocalDateTime.of(2026, 11, 26, 19, 0);
        TeamInvitation pending = TeamInvitation.create(10L, 1L, 2L, now);
        ReflectionTestUtils.setField(pending, "id", 101L);
        TeamInvitation declined = TeamInvitation.create(10L, 1L, 3L, now);
        declined.decline(now.plusMinutes(5));
        ReflectionTestUtils.setField(declined, "id", 102L);

        given(teamInvitationRepository.findByIdIn(Set.of(101L, 102L, 999L)))
                .willReturn(List.of(pending, declined));

        Map<Long, String> statuses =
                queryService.findStatuses(Arrays.asList(101L, 102L, 999L, null));
        assertThat(statuses)
                .containsExactlyInAnyOrderEntriesOf(Map.of(101L, "PENDING", 102L, "DECLINED"));
    }

    @Test
    @DisplayName("getMyRecruitingPosts는 PostQueryService 결과를 초대 모달 응답 DTO로 변환한다")
    void 내_모집중글_조회() {
        RecruitPost post = mock(RecruitPost.class);
        given(post.getId()).willReturn(20L);
        given(post.getTitle()).willReturn("AI 해커톤 팀원 모집");
        given(post.getCurrentCount()).willReturn(2);
        given(post.getTargetCount()).willReturn(4);
        given(postQueryService.getMyRecruitingPosts(1L)).willReturn(List.of(post));

        List<RecruitingPostResponse> list = queryService.getMyRecruitingPosts(1L);
        assertThat(list).containsExactly(new RecruitingPostResponse(20L, "AI 해커톤 팀원 모집", 2, 4));
    }
}
