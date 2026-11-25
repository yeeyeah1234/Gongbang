package com.gongbang.post.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gongbang.application.service.ApplicationCloseService;
import com.gongbang.member.service.AdminMemberService;
import com.gongbang.notification.domain.NotificationType;
import com.gongbang.notification.service.NotificationService;
import com.gongbang.post.domain.*;
import com.gongbang.post.repository.RecruitPostRepository;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AdminPostServiceTest {
    final RecruitPostRepository repository = mock(RecruitPostRepository.class);
    final ApplicationCloseService applications = mock(ApplicationCloseService.class);
    final NotificationService notifications = mock(NotificationService.class);
    final AdminPostService service =
            new AdminPostService(
                    repository,
                    mock(AdminMemberService.class),
                    applications,
                    notifications,
                    mock(EntityManager.class));

    @Test
    void 강제숨김은_모집을종료하고_닫힌대기지원자에게만_한번알린다() {
        var post = org.springframework.beans.BeanUtils.instantiateClass(RecruitPost.class);
        ReflectionTestUtils.setField(post, "id", 8L);
        ReflectionTestUtils.setField(post, "title", "모집글");
        when(repository.findByIdForUpdate(8L)).thenReturn(Optional.of(post));
        when(applications.closePendingApplications(eq(8L), any())).thenReturn(List.of(2L, 3L));
        service.hide(1L, 8L);
        assertThat(post.getDeletedAt()).isNotNull();
        assertThat(post.getStatus()).isEqualTo(PostStatus.CLOSED);
        verify(applications).closePendingApplications(eq(8L), any());
        verify(applications, never()).findAllApplicantIds(anyLong());
        for (long id : List.of(2L, 3L))
            verify(notifications)
                    .send(
                            eq(id),
                            eq(NotificationType.POST_DELETED),
                            isNull(),
                            eq("POST"),
                            eq(8L),
                            anyString(),
                            eq("/notifications"));
        service.hide(1L, 8L);
        verify(notifications, times(2))
                .send(anyLong(), any(), any(), anyString(), anyLong(), anyString(), anyString());
    }
}
