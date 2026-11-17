package com.gongbang.notification.service;

import com.gongbang.member.domain.Member;
import com.gongbang.member.service.MemberService;
import com.gongbang.notification.domain.Notification;
import com.gongbang.notification.domain.NotificationSetting;
import com.gongbang.notification.domain.NotificationType;
import com.gongbang.notification.domain.SettingType;
import com.gongbang.notification.repository.NotificationRepository;
import com.gongbang.notification.repository.NotificationSettingRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * NOTI-04 알림 만들기 공통 함수. 다른 담당자는 이것만 부른다 (AGENTS 8).
 *
 * <pre>
 * notificationService.send(받는 사람 id, NotificationType.TEAM_INVITE, 보낸 사람 id,
 *         "INVITATION", 초대 id, "‘닉네임’님이 ‘공모전명 · 모집글 제목’ 팀에 초대했어요", "/posts/12");
 * </pre>
 *
 * 수신 설정은 여기서 확인한다(부르는 쪽은 확인하지 않음). 채팅 메시지는 부르지 않는다(⑤).
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final String COMMENT_TARGET = "POST";

    private final NotificationRepository notificationRepository;
    private final NotificationSettingRepository settingRepository;
    private final MemberService memberService;
    private final EmailOutboxService emailOutboxService;

    /**
     * @return 만든(또는 합친) 알림. 사이트 알림을 끈 종류·나에게 하는 알림이면 빈 값
     */
    @Transactional
    public Optional<Notification> send(
            Long receiverId,
            NotificationType type,
            Long actorId,
            String targetType,
            Long targetId,
            String message,
            String linkUrl) {
        // ② 내가 한 행동은 나에게 알리지 않는다 (정해진 시각 일정 알림은 예외 — actor가 없음)
        if (actorId != null && actorId.equals(receiverId)) {
            return Optional.empty();
        }
        // 탈퇴·삭제 회원은 조용히 건너뛴다. 예외로 잡으면 부르는 쪽 트랜잭션 전체가 rollback-only가 된다
        Member receiver = memberService.findActiveMember(receiverId).orElse(null);
        if (receiver == null) {
            return Optional.empty();
        }
        LocalDateTime now = LocalDateTime.now();
        boolean siteEnabled = true;
        boolean emailEnabled = true;
        SettingType settingType = type.getSettingType();
        if (settingType != null) {
            Optional<NotificationSetting> setting =
                    settingRepository.findById(
                            new NotificationSetting.Key(receiverId, settingType));
            siteEnabled = setting.map(NotificationSetting::isSiteEnabled).orElse(true);
            emailEnabled = setting.map(NotificationSetting::isEmailEnabled).orElse(true);
        }

        Notification notification = null;
        if (siteEnabled) {
            notification =
                    createOrMerge(
                            receiverId, type, actorId, targetType, targetId, message, linkUrl, now);
        }
        // ⑥ 이메일 대상이면 메일 대기열 (③ 이메일을 끈 종류는 넣지 않는다)
        if (emailEnabled && type.getSendMode() != null && receiver.getEmail() != null) {
            String mailMessage = notification != null ? notification.getMessage() : message;
            emailOutboxService.enqueueNotification(
                    receiver,
                    type,
                    notification == null ? null : notification.getId(),
                    mailMessage,
                    linkUrl,
                    now);
        }
        return Optional.ofNullable(notification);
    }

    private Notification createOrMerge(
            Long receiverId,
            NotificationType type,
            Long actorId,
            String targetType,
            Long targetId,
            String message,
            String linkUrl,
            LocalDateTime now) {
        boolean groupable =
                (type == NotificationType.COMMENT || type == NotificationType.REPLY)
                        && COMMENT_TARGET.equals(targetType)
                        && targetId != null;
        if (groupable) {
            Optional<Notification> existing =
                    notificationRepository
                            .findFirstByReceiverIdAndTypeAndTargetTypeAndTargetIdAndReadFalse(
                                    receiverId, type, targetType, targetId);
            if (existing.isPresent()) {
                Notification n = existing.get();
                // ④ 「A님 외 2명이…」
                String actorName = actorId == null ? "누군가" : memberService.displayName(actorId);
                String verb = type == NotificationType.REPLY ? "답글을" : "댓글을";
                n.merge(
                        actorId,
                        actorName + "님 외 " + n.getGroupCount() + "명이 " + verb + " 남겼어요",
                        now);
                return n;
            }
        }
        return notificationRepository.save(
                new Notification(
                        receiverId, type, actorId, targetType, targetId, message, linkUrl, now));
    }
}
