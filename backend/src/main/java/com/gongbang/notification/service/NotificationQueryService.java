package com.gongbang.notification.service;

import com.gongbang.global.common.PageResponse;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.invitation.service.InvitationQueryService;
import com.gongbang.member.service.MemberService;
import com.gongbang.notification.domain.Notification;
import com.gongbang.notification.domain.NotificationType;
import com.gongbang.notification.dto.NotificationResponse;
import com.gongbang.notification.repository.NotificationRepository;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** NOTI-01·02·03 알림 보기·읽음·삭제 */
@Service
@RequiredArgsConstructor
public class NotificationQueryService {

    /** NOTI-01 ⑥ */
    public static final int KEEP_DAYS = 30;

    private static final int MAX_SIZE = 50;

    private final NotificationRepository notificationRepository;
    private final MemberService memberService;
    private final InvitationQueryService invitationQueryService;
    private final com.gongbang.member.service.AdminInvitationService adminInvitations;

    @Transactional(readOnly = true)
    public long unreadCount(Long memberId) {
        return notificationRepository.countByReceiverIdAndReadFalse(memberId);
    }

    /** 알림 목록과 팀 초대의 현재 상태를 함께 조회한다. */
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(
            Long memberId, int page, int size, boolean unreadOnly, String category) {
        if (page < 0 || size < 1 || size > MAX_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        List<NotificationType> types =
                Arrays.stream(NotificationType.values())
                        .filter(
                                t ->
                                        category == null
                                                || category.isBlank()
                                                || t.getCategory().name().equals(category))
                        .toList();
        if (types.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        Page<Notification> result =
                notificationRepository.findPage(
                        memberId, unreadOnly, types, PageRequest.of(page, size));
        var invitationIds =
                result.getContent().stream()
                        .filter(
                                n ->
                                        n.getType() == NotificationType.TEAM_INVITE
                                                && "INVITATION".equals(n.getTargetType())
                                                && n.getTargetId() != null)
                        .map(Notification::getTargetId)
                        .distinct()
                        .toList();
        var statuses =
                invitationIds.isEmpty()
                        ? java.util.Map.<Long, String>of()
                        : invitationQueryService.findStatuses(invitationIds);
        var adminIds =
                result.getContent().stream()
                        .filter(
                                n ->
                                        n.getType() == NotificationType.ADMIN_INVITE
                                                && "ADMIN_INVITATION".equals(n.getTargetType())
                                                && n.getTargetId() != null)
                        .map(Notification::getTargetId)
                        .distinct()
                        .toList();
        var adminStatuses =
                adminIds.isEmpty()
                        ? java.util.Map.<Long, String>of()
                        : adminInvitations.findStatuses(adminIds);
        List<NotificationResponse> items =
                result.getContent().stream()
                        .map(
                                n ->
                                        NotificationResponse.of(
                                                n,
                                                n.getActorId() == null
                                                        ? null
                                                        : new NotificationResponse.Actor(
                                                                n.getActorId(),
                                                                memberService.displayName(
                                                                        n.getActorId())),
                                                (n.getType() == NotificationType.ADMIN_INVITE
                                                                ? adminStatuses
                                                                : statuses)
                                                        .get(n.getTargetId())))
                        .toList();
        return PageResponse.of(items, page, size, result.getTotalElements());
    }

    @Transactional
    public void markRead(Long memberId, Long notificationId) {
        own(memberId, notificationId).markRead(LocalDateTime.now());
    }

    @Transactional
    public void markAllRead(Long memberId) {
        notificationRepository.markAllRead(memberId, LocalDateTime.now());
    }

    /** NOTI-03 ⑤ 메일 링크로 들어온 화면 */
    @Transactional
    public void markReadByLink(Long memberId, String linkUrl) {
        notificationRepository.markReadByLink(memberId, linkUrl, LocalDateTime.now());
    }

    @Transactional
    public void delete(Long memberId, Long notificationId) {
        notificationRepository.delete(own(memberId, notificationId));
    }

    @Transactional
    public void deleteRead(Long memberId) {
        notificationRepository.deleteRead(memberId);
    }

    /** NOTI-01 ⑥ 매일 새벽 3시 30일 지난 알림 삭제 */
    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
    @Transactional
    public void deleteOld() {
        notificationRepository.deleteOlderThan(LocalDateTime.now().minusDays(KEEP_DAYS));
    }

    private Notification own(Long memberId, Long notificationId) {
        Notification n =
                notificationRepository
                        .findById(notificationId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!n.getReceiverId().equals(memberId)) {
            throw new BusinessException(ErrorCode.NOT_OWNER);
        }
        return n;
    }
}
