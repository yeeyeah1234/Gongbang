package com.gongbang.post.service;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.gongbang.member.service.MemberService;
import com.gongbang.notification.domain.NotificationType;
import com.gongbang.notification.service.NotificationService;
import com.gongbang.post.domain.RecruitPost;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** INT-003 모집 알림 문구·이동 주소. */
@ExtendWith(MockitoExtension.class)
class RecruitNotifierTest {

    @Mock private NotificationService notificationService;
    @Mock private MemberService memberService;

    private RecruitNotifier recruitNotifier;
    private RecruitPost post;

    @BeforeEach
    void setUp() {
        recruitNotifier = new RecruitNotifier(notificationService, memberService);
        LocalDate start = LocalDate.of(2026, 11, 17);
        post = RecruitPost.withContest(7L, 3L, "AI 팀원 모집", "내용", 4, start, start, null);
        ReflectionTestUtils.setField(post, "id", 10L);
    }

    @Test
    @DisplayName("수락 알림: 「‘제목’ 팀에 합류했어요」, 누르면 모집글 상세")
    void 수락_알림() {
        recruitNotifier.accepted(post, 20L, 7L);

        verify(notificationService)
                .send(
                        20L,
                        NotificationType.APPLY_ACCEPTED,
                        7L,
                        "POST",
                        10L,
                        "‘AI 팀원 모집’ 팀에 합류했어요",
                        "/posts/10");
    }

    @Test
    @DisplayName("자동 거절 알림: 「‘제목’ 지원이 거절되었어요」, 보낸 사람 없음(시스템)")
    void 자동_거절_알림() {
        recruitNotifier.rejected(post, 21L, null);

        verify(notificationService)
                .send(
                        21L,
                        NotificationType.APPLY_REJECTED,
                        null,
                        "POST",
                        10L,
                        "‘AI 팀원 모집’ 지원이 거절되었어요",
                        "/posts/10");
    }

    @Test
    @DisplayName("새 지원 알림: 작성자에게 「‘닉네임’님이 ‘제목’에 지원했어요」, 누르면 지원자 관리 모달")
    void 새_지원_알림() {
        given(memberService.displayName(20L)).willReturn("지원자");

        recruitNotifier.newApplication(post, 20L);

        verify(notificationService)
                .send(
                        7L,
                        NotificationType.APPLY_NEW,
                        20L,
                        "POST",
                        10L,
                        "‘지원자’님이 ‘AI 팀원 모집’에 지원했어요",
                        "/posts/10?applicants=open");
    }

    @Test
    @DisplayName("삭제 알림: 「‘제목’ 모집글이 삭제되었어요」, 누르면 모집글 목록")
    void 삭제_알림() {
        recruitNotifier.postDeleted(post, 21L, 7L);

        verify(notificationService)
                .send(
                        21L,
                        NotificationType.POST_DELETED,
                        7L,
                        "POST",
                        10L,
                        "‘AI 팀원 모집’ 모집글이 삭제되었어요",
                        "/posts");
    }
}
