package com.gongbang.invitation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gongbang.contest.dto.ContestSummary;
import com.gongbang.contest.service.ContestLookupService;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.invitation.domain.InvitationStatus;
import com.gongbang.invitation.domain.TeamInvitation;
import com.gongbang.invitation.dto.InvitationAcceptResponse;
import com.gongbang.invitation.dto.InvitationCreateRequest;
import com.gongbang.invitation.dto.InvitationCreateResponse;
import com.gongbang.invitation.repository.TeamInvitationRepository;
import com.gongbang.invitation.repository.TeamInvitationRepository.InvitationMeta;
import com.gongbang.member.domain.Member;
import com.gongbang.member.service.MemberService;
import com.gongbang.notification.domain.NotificationType;
import com.gongbang.notification.service.NotificationService;
import com.gongbang.post.domain.PostStatus;
import com.gongbang.post.domain.RecruitPost;
import com.gongbang.post.dto.TeamJoinResult;
import com.gongbang.post.service.PostCommandService;
import com.gongbang.post.service.PostMembershipService;
import com.gongbang.post.service.PostQueryService;
import com.gongbang.post.service.TeamJoinService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InvitationCommandServiceTest {

    @Mock private TeamInvitationRepository teamInvitationRepository;
    @Mock private PostCommandService postCommandService;
    @Mock private PostQueryService postQueryService;
    @Mock private PostMembershipService postMembershipService;
    @Mock private TeamJoinService teamJoinService;
    @Mock private InvitationCancelService invitationCancelService;
    @Mock private MemberService memberService;
    @Mock private ContestLookupService contestLookupService;
    @Mock private NotificationService notificationService;

    private InvitationCommandService commandService;

    @BeforeEach
    void setUp() {
        commandService =
                new InvitationCommandService(
                        teamInvitationRepository,
                        postCommandService,
                        postQueryService,
                        postMembershipService,
                        teamJoinService,
                        invitationCancelService,
                        memberService,
                        contestLookupService,
                        notificationService);
    }

    @Test
    @DisplayName("팀장이 모집중인 글로 활성 회원을 초대하면 저장하고 TEAM_INVITE 알림을 보낸다")
    void 팀_초대_정상_및_알림() {
        RecruitPost post = samplePost(10L, 1L, true, false, 5L, "프론트 모집");
        Member invitee = mock(Member.class);
        given(invitee.isSuspended()).willReturn(false);

        given(postQueryService.getActivePost(10L)).willReturn(post);
        given(memberService.findActiveMember(2L)).willReturn(Optional.of(invitee));
        given(postMembershipService.isActiveMember(10L, 2L)).willReturn(false);
        given(teamInvitationRepository.findByPostIdAndInviteeIdForUpdate(10L, 2L))
                .willReturn(Optional.empty());
        given(teamInvitationRepository.saveAndFlush(any(TeamInvitation.class)))
                .willAnswer(
                        inv -> {
                            TeamInvitation entity = inv.getArgument(0);
                            ReflectionTestUtils.setField(entity, "id", 55L);
                            return entity;
                        });
        given(memberService.displayName(1L)).willReturn("건우");
        given(contestLookupService.findSummary(5L))
                .willReturn(
                        Optional.of(
                                new ContestSummary(
                                        5L, "공방 해커톤", "IT", LocalDate.of(2026, 12, 31), null)));

        InvitationCreateResponse response =
                commandService.invite(1L, new InvitationCreateRequest(10L, 2L));

        assertThat(response.invitationId()).isEqualTo(55L);
        verify(notificationService)
                .send(
                        eq(2L),
                        eq(NotificationType.TEAM_INVITE),
                        eq(1L),
                        eq("INVITATION"),
                        eq(55L),
                        eq("‘건우’님이 ‘공방 해커톤 · 프론트 모집’ 팀에 초대했어요"),
                        eq("/posts/10"));
    }

    @Test
    @DisplayName("거절되었던 회원을 다시 초대하면 기존 행을 PENDING으로 되돌리고, 이미 PENDING이면 ALREADY_INVITED를 던진다")
    void 재초대_복귀_및_중복대기_차단() {
        RecruitPost post = samplePost(10L, 1L, true, false, null, "팀원 구함");
        Member invitee = mock(Member.class);
        given(invitee.isSuspended()).willReturn(false);

        TeamInvitation existing =
                TeamInvitation.create(10L, 1L, 2L, LocalDateTime.of(2026, 11, 20, 10, 0));
        ReflectionTestUtils.setField(existing, "id", 77L);
        existing.decline(LocalDateTime.of(2026, 11, 20, 11, 0));

        given(postQueryService.getActivePost(10L)).willReturn(post);
        given(memberService.findActiveMember(2L)).willReturn(Optional.of(invitee));
        given(postMembershipService.isActiveMember(10L, 2L)).willReturn(false);
        given(teamInvitationRepository.findByPostIdAndInviteeIdForUpdate(10L, 2L))
                .willReturn(Optional.of(existing));
        given(memberService.displayName(1L)).willReturn("건우");

        InvitationCreateResponse res =
                commandService.invite(1L, new InvitationCreateRequest(10L, 2L));
        assertThat(res.invitationId()).isEqualTo(77L);
        assertThat(existing.getStatus()).isEqualTo(InvitationStatus.PENDING);
        assertThat(existing.getRespondedAt()).isNull();

        assertThatThrownBy(() -> commandService.invite(1L, new InvitationCreateRequest(10L, 2L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ALREADY_INVITED);
    }

    @Test
    @DisplayName("본인 초대, 타인 모집글, 모집완료 글, 정원 마감, 기존 팀원은 각각 정해진 에러 코드로 차단한다")
    void 초대_사전검증_예외() {
        assertThatThrownBy(() -> commandService.invite(1L, new InvitationCreateRequest(10L, 1L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CANNOT_INVITE_SELF);

        RecruitPost othersPost = samplePost(10L, 99L, true, false, null, "남의 글");
        given(postQueryService.getActivePost(10L)).willReturn(othersPost);
        assertThatThrownBy(() -> commandService.invite(1L, new InvitationCreateRequest(10L, 2L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_POST_AUTHOR);

        RecruitPost completedPost = samplePost(11L, 1L, false, false, null, "완료 글");
        given(postQueryService.getActivePost(11L)).willReturn(completedPost);
        assertThatThrownBy(() -> commandService.invite(1L, new InvitationCreateRequest(11L, 2L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.POST_COMPLETED);

        RecruitPost fullPost = samplePost(12L, 1L, true, true, null, "만석 글");
        given(postQueryService.getActivePost(12L)).willReturn(fullPost);
        assertThatThrownBy(() -> commandService.invite(1L, new InvitationCreateRequest(12L, 2L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.POST_FULL);

        RecruitPost okPost = samplePost(13L, 1L, true, false, null, "모집중");
        Member invitee = mock(Member.class);
        given(invitee.isSuspended()).willReturn(false);
        given(postQueryService.getActivePost(13L)).willReturn(okPost);
        given(memberService.findActiveMember(2L)).willReturn(Optional.of(invitee));
        given(postMembershipService.isActiveMember(13L, 2L)).willReturn(true);
        assertThatThrownBy(() -> commandService.invite(1L, new InvitationCreateRequest(13L, 2L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ALREADY_TEAM_MEMBER);
    }

    @Test
    @DisplayName(
            "초대 수락 시 TeamJoinService.joinByInvitation을 부르고, 정원이 차 COMPLETED가 되면 남은 대기 초대도 취소한다")
    void 초대_수락_및_자동모집완료시_잔여초대취소() {
        InvitationMeta meta = metaOf(10L, 2L, InvitationStatus.PENDING);
        TeamInvitation entity = TeamInvitation.create(10L, 1L, 2L, LocalDateTime.now());
        ReflectionTestUtils.setField(entity, "id", 88L);

        given(teamInvitationRepository.findMetaById(88L)).willReturn(Optional.of(meta));
        given(teamInvitationRepository.findByIdForUpdate(88L)).willReturn(Optional.of(entity));
        given(teamJoinService.joinByInvitation(eq(10L), eq(2L), any(LocalDateTime.class)))
                .willAnswer(
                        inv -> {
                            assertThat(entity.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
                            return new TeamJoinResult(300L, 4, 4, PostStatus.COMPLETED);
                        });

        InvitationAcceptResponse response = commandService.accept(88L, 2L);

        assertThat(response.teamId()).isEqualTo(300L);
        assertThat(entity.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
        assertThat(entity.getRespondedAt()).isNotNull();

        InOrder order =
                inOrder(
                        postCommandService,
                        teamInvitationRepository,
                        teamJoinService,
                        invitationCancelService);
        order.verify(postCommandService).lockPost(10L);
        order.verify(teamInvitationRepository).findByIdForUpdate(88L);
        order.verify(teamInvitationRepository).flush();
        order.verify(teamJoinService).joinByInvitation(eq(10L), eq(2L), any(LocalDateTime.class));
        order.verify(invitationCancelService)
                .cancelPendingByPostId(eq(10L), any(LocalDateTime.class));
    }

    @Test
    @DisplayName(
            "초대받은 본인이 아니거나 이미 마감된 초대는 수락·거절 시 각각 NOT_INVITEE(403), INVITATION_CLOSED(409)를 던진다")
    void 초대_수락_거절_권한_및_상태검증() {
        given(teamInvitationRepository.findMetaById(88L))
                .willReturn(Optional.of(metaOf(10L, 2L, InvitationStatus.PENDING)));
        assertThatThrownBy(() -> commandService.accept(88L, 9L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_INVITEE);

        given(teamInvitationRepository.findMetaById(89L))
                .willReturn(Optional.of(metaOf(10L, 2L, InvitationStatus.CANCELED)));
        assertThatThrownBy(() -> commandService.accept(89L, 2L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVITATION_CLOSED);
        verify(teamJoinService, never()).joinByInvitation(any(), any(), any());

        TeamInvitation pending = TeamInvitation.create(10L, 1L, 2L, LocalDateTime.now());
        given(teamInvitationRepository.findByIdForUpdate(90L)).willReturn(Optional.of(pending));
        commandService.decline(90L, 2L);
        assertThat(pending.getStatus()).isEqualTo(InvitationStatus.DECLINED);

        assertThatThrownBy(() -> commandService.decline(90L, 2L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVITATION_CLOSED);
    }

    private static RecruitPost samplePost(
            Long id,
            Long authorId,
            boolean recruiting,
            boolean full,
            Long contestId,
            String title) {
        RecruitPost post = mock(RecruitPost.class);
        org.mockito.Mockito.lenient().when(post.getId()).thenReturn(id);
        org.mockito.Mockito.lenient().when(post.getAuthorId()).thenReturn(authorId);
        org.mockito.Mockito.lenient().when(post.isRecruiting()).thenReturn(recruiting);
        org.mockito.Mockito.lenient().when(post.isFull()).thenReturn(full);
        org.mockito.Mockito.lenient().when(post.getContestId()).thenReturn(contestId);
        org.mockito.Mockito.lenient().when(post.getTitle()).thenReturn(title);
        return post;
    }

    private static InvitationMeta metaOf(Long postId, Long inviteeId, InvitationStatus status) {
        return new InvitationMeta() {
            @Override
            public Long getPostId() {
                return postId;
            }

            @Override
            public Long getInviteeId() {
                return inviteeId;
            }

            @Override
            public InvitationStatus getStatus() {
                return status;
            }
        };
    }
}
