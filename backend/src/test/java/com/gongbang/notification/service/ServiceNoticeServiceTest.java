package com.gongbang.notification.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gongbang.auth.domain.LoginMember;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.domain.*;
import com.gongbang.member.service.MemberService;
import com.gongbang.notification.domain.*;
import com.gongbang.notification.dto.ServiceNoticeRequest;
import com.gongbang.notification.repository.ServiceNoticeRepository;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ServiceNoticeServiceTest {
    final ServiceNoticeRepository repository = mock(ServiceNoticeRepository.class);
    final NotificationService notifications = mock(NotificationService.class);
    final MemberService members = mock(MemberService.class);
    final ServiceNoticeService service =
            new ServiceNoticeService(repository, notifications, members);

    void admin() {
        var m = Member.createEmailMember("admin@example.com", "hash", "관리자");
        m.grantAdmin();
        when(members.getActiveMember(1L)).thenReturn(m);
    }

    @Test
    void 등록만_전체회원사이트알림을보내고_이메일대상이아니다() {
        admin();
        when(members.findActiveMemberIds()).thenReturn(List.of(1L, 2L));
        when(repository.save(any()))
                .thenAnswer(
                        inv -> {
                            ServiceNotice n = inv.getArgument(0);
                            ReflectionTestUtils.setField(n, "id", 8L);
                            return n;
                        });
        service.create(new LoginMember(1L, MemberRole.ADMIN), new ServiceNoticeRequest("공지", "내용"));
        verify(notifications, times(2))
                .send(
                        anyLong(),
                        eq(NotificationType.SERVICE_NOTICE),
                        isNull(),
                        eq("SERVICE_NOTICE"),
                        eq(8L),
                        eq("공지"),
                        eq("/service-notices/8"));
        assertThat(NotificationType.SERVICE_NOTICE.getSendMode()).isNull();
    }

    @Test
    void 삭제후_공개조회에서제외된다() {
        admin();
        var n = new ServiceNotice(1L, "공지", "내용");
        when(repository.findForUpdate(8L)).thenReturn(Optional.of(n));
        when(repository.findById(8L)).thenReturn(Optional.of(n));
        service.delete(new LoginMember(1L, MemberRole.ADMIN), 8L);
        assertThat(n.getDeletedAt()).isNotNull();
        assertThatThrownBy(() -> service.get(8L)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(notifications);
    }
}
