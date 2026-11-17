package com.gongbang.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gongbang.member.service.MemberService;
import com.gongbang.notification.domain.NotificationType;
import com.gongbang.notification.repository.NotificationRepository;
import com.gongbang.notification.repository.NotificationSettingRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NotificationServiceTest {

    private final NotificationRepository notificationRepository =
            mock(NotificationRepository.class);
    private final MemberService memberService = mock(MemberService.class);
    private final NotificationService service =
            new NotificationService(
                    notificationRepository,
                    mock(NotificationSettingRepository.class),
                    memberService,
                    mock(EmailOutboxService.class));

    @Test
    void 탈퇴한_회원에게는_예외_없이_알림을_건너뛴다() {
        when(memberService.findActiveMember(2L)).thenReturn(Optional.empty());

        var result =
                service.send(
                        2L,
                        NotificationType.values()[0],
                        1L,
                        "APPLICATION",
                        10L,
                        "알림",
                        "/posts/1");

        assertThat(result).isEmpty();
        // 예외를 던지는 조회를 부르면 부르는 쪽 트랜잭션이 rollback-only가 된다
        verify(memberService, never()).getActiveMember(any());
        verify(notificationRepository, never()).save(any());
    }
}
