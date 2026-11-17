package com.gongbang.notification.service;

import com.gongbang.member.repository.BookmarkRepository;
import com.gongbang.notification.domain.NotificationType;
import com.gongbang.notification.domain.ScheduledNotification;
import com.gongbang.notification.repository.ScheduledNotificationRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * NOTI-05 예약 알림. 다른 담당자는 schedule / cancel만 부른다.
 *
 * <pre>
 * // 이상현 CAL-07: 일정을 만들거나 고칠 때
 * scheduledNotificationService.schedule(NotificationType.EVENT_REMIND, "EVENT", eventId, fireAt);
 * scheduledNotificationService.cancel("EVENT", eventId); // 지울 때
 * </pre>
 *
 * 5분마다 시각이 된 행을 꺼내 handlers(대상 종류별로 받는 사람·문구를 아는 쪽이 등록)로 보낸다.
 */
@Service
@RequiredArgsConstructor
public class ScheduledNotificationService {

    private final ScheduledNotificationRepository repository;
    private final NotificationService notificationService;
    private final BookmarkRepository bookmarkRepository;
    private final java.util.Map<String, Consumer<ScheduledNotification>> handlers =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** 대상 종류(EVENT·TEAM 등)를 아는 담당자가 받는 사람·문구를 만들어 보내는 함수를 등록한다 */
    public void registerHandler(String targetType, Consumer<ScheduledNotification> handler) {
        handlers.put(targetType, handler);
    }

    /** ② 일정이 바뀌면 기존 것을 취소하고 새로 잡는다. ③ 이미 지난 시각이면 넣지 않는다 */
    @Transactional
    public void schedule(
            NotificationType type, String targetType, Long targetId, LocalDateTime fireAt) {
        cancel(targetType, targetId);
        if (!fireAt.isAfter(LocalDateTime.now())) {
            return;
        }
        repository.save(new ScheduledNotification(type, targetType, targetId, fireAt));
    }

    @Transactional
    public void cancel(String targetType, Long targetId) {
        repository
                .findByTargetTypeAndTargetIdAndStatus(
                        targetType, targetId, ScheduledNotification.Status.PENDING)
                .forEach(ScheduledNotification::cancel);
    }

    /** ① 5분마다 확인 */
    @Scheduled(fixedDelay = 300_000)
    @Transactional
    public void fireDue() {
        List<ScheduledNotification> due =
                repository.findTop200ByStatusAndFireAtLessThanEqualOrderByFireAtAsc(
                        ScheduledNotification.Status.PENDING, LocalDateTime.now());
        for (ScheduledNotification item : due) {
            Consumer<ScheduledNotification> handler = handlers.get(item.getTargetType());
            if (handler == null) {
                // TODO(확인 필요): EVENT(이상현 CAL-07)·TEAM(COM-05) 핸들러는 각 담당이 registerHandler로 등록한다
                continue;
            }
            handler.accept(item);
            item.sent();
        }
    }

    /**
     * ① 북마크 공모전 마감 D-1 오전 9시 (하루 1번 메일 모음 = NOTI-06 ③). TODO(확인 필요): 「그 공모전 팀에 들어가 있는 회원은 제외」는
     * team·recruit_post가 develop에 들어오면 붙인다.
     */
    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    @Transactional
    public void notifyBookmarkDeadline() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        for (Object[] row : bookmarkRepository.findBookmarksByDeadline(tomorrow)) {
            Long memberId = ((Number) row[0]).longValue();
            Long contestId = ((Number) row[1]).longValue();
            String title = (String) row[2];
            notificationService.send(
                    memberId,
                    NotificationType.BOOKMARK_DEADLINE,
                    null,
                    "CONTEST",
                    contestId,
                    "북마크한 '" + title + "'이 내일 마감돼요.",
                    "/contests/" + contestId);
        }
    }
}
