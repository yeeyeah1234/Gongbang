package com.gongbang.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.gongbang.member.domain.Member;
import com.gongbang.member.service.MemberService;
import com.gongbang.notification.domain.Notification;
import com.gongbang.notification.domain.NotificationType;
import com.gongbang.notification.repository.NotificationRepository;
import com.gongbang.notification.repository.NotificationSettingRepository;
import com.gongbang.team.domain.TeamRole;
import com.gongbang.team.repository.TeamMemberRepository;
import com.gongbang.team.service.TeamMemberService;
import com.gongbang.workspace.notice.event.NoticeCreatedEvent;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.SimpleTransactionStatus;

class NoticeCreatedListenerTest {

    // 발행 측이 전달한 주소를 보존하는지 확인하는 테스트 값이며 실제 라우트 계약이 아니다.
    private static final String NOTICE_LINK = "/test/teams/10/notices/20";

    private static NoticeCreatedEvent event(Long memberId) {
        return new NoticeCreatedEvent(10L, 20L, memberId, "기획안 1차 마감 일정 안내", NOTICE_LINK);
    }

    @Configuration
    @EnableTransactionManagement
    static class TransactionConfig {}

    @Test
    void 커밋_후_발행된_이벤트를_새_트랜잭션에서_처리하고_작성자와_탈퇴회원을_제외한다() {
        TeamMemberRepository teams = mock(TeamMemberRepository.class);
        NotificationRepository notifications = mock(NotificationRepository.class);
        MemberService members = mock(MemberService.class);
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(teams.findActiveMemberIdsByRole(10L, TeamRole.MEMBER)).thenReturn(List.of(2L, 3L, 4L));
        when(members.findActiveMember(3L)).thenReturn(Optional.of(mock(Member.class)));
        when(members.findActiveMember(4L)).thenReturn(Optional.empty());
        when(notifications.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(TransactionConfig.class);
            context.registerBean(PlatformTransactionManager.class, () -> transactions);
            context.registerBean(TeamMemberRepository.class, () -> teams);
            context.registerBean(
                    NotificationService.class,
                    () ->
                            new NotificationService(
                                    notifications,
                                    mock(NotificationSettingRepository.class),
                                    members,
                                    mock(EmailOutboxService.class)));
            context.register(
                    TeamMemberService.class,
                    NoticeNotificationService.class,
                    NoticeCreatedListener.class);
            context.refresh();

            // 발행자가 커밋을 마친 뒤 발행: 활성 트랜잭션이 없어도 이벤트를 수신한다.
            context.publishEvent(event(2L));

            ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
            verify(notifications).save(saved.capture());
            assertThat(saved.getValue().getReceiverId()).isEqualTo(3L);
            assertThat(saved.getValue().getActorId()).isEqualTo(2L);
            assertThat(saved.getValue().getType()).isEqualTo(NotificationType.NOTICE_NEW);
            assertThat(saved.getValue().getTargetType()).isEqualTo("NOTICE");
            assertThat(saved.getValue().getTargetId()).isEqualTo(20L);
            assertThat(saved.getValue().getMessage()).isEqualTo("새 공지: 기획안 1차 마감 일정 안내");
            assertThat(saved.getValue().getLinkUrl()).isEqualTo(NOTICE_LINK);
            verify(members, never()).findActiveMember(2L);
            verify(teams).findActiveMemberIdsByRole(10L, TeamRole.MEMBER);

            ArgumentCaptor<TransactionDefinition> definitions =
                    ArgumentCaptor.forClass(TransactionDefinition.class);
            verify(transactions, atLeastOnce()).getTransaction(definitions.capture());
            assertThat(definitions.getAllValues().get(0).getPropagationBehavior())
                    .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            verify(transactions, atLeastOnce()).commit(any());
        }
    }

    @Test
    void 현재_일반_팀원이_없으면_알림을_보내지_않는다() {
        TeamMemberRepository teams = mock(TeamMemberRepository.class);
        NotificationService notifications = mock(NotificationService.class);
        when(teams.findActiveMemberIdsByRole(10L, TeamRole.MEMBER)).thenReturn(List.of());

        new NoticeNotificationService(new TeamMemberService(teams), notifications).send(event(1L));

        verifyNoInteractions(notifications);
    }

    @Test
    void 알림_저장_실패가_이미_저장된_공지_등록을_실패로_응답하게_하지_않는다() {
        NoticeNotificationService service = mock(NoticeNotificationService.class);
        NoticeCreatedEvent event = event(1L);
        doThrow(new IllegalStateException("DB 오류")).when(service).send(event);

        assertDoesNotThrow(() -> new NoticeCreatedListener(service).handle(event));
        verify(service).send(event);
    }
}
