package com.gongbang.notification.service;

import com.gongbang.auth.domain.LoginMember;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.service.MemberService;
import com.gongbang.notification.domain.NotificationType;
import com.gongbang.notification.domain.ServiceNotice;
import com.gongbang.notification.dto.ServiceNoticeRequest;
import com.gongbang.notification.dto.ServiceNoticeResponse;
import com.gongbang.notification.repository.ServiceNoticeRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** NOTI-07 운영 공지 */
@Service
@RequiredArgsConstructor
public class ServiceNoticeService {
    private final com.gongbang.admin.service.AdminActivityWriter audit;

    private final ServiceNoticeRepository repository;
    private final NotificationService notificationService;
    private final MemberService memberService;
    private final EmailOutboxService emailOutboxService;

    public record AdminNotice(
            Long id,
            String title,
            String content,
            LocalDateTime createdAt,
            LocalDateTime deletedAt,
            boolean policyChange,
            LocalDateTime emailQueuedAt) {}

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<AdminNotice> list(LoginMember admin, int page) {
        checkAdmin(admin);
        if (page < 0) throw new BusinessException(ErrorCode.INVALID_INPUT);
        return repository
                .findAll(
                        org.springframework.data.domain.PageRequest.of(
                                page,
                                20,
                                org.springframework.data.domain.Sort.by("id").descending()))
                .map(
                        n ->
                                new AdminNotice(
                                        n.getId(),
                                        n.getTitle(),
                                        n.getContent(),
                                        n.getCreatedAt(),
                                        n.getDeletedAt(),
                                        n.isPolicyChange(),
                                        n.getEmailQueuedAt()));
    }

    @Transactional(readOnly = true)
    public java.util.Map<Long, String> titles(LoginMember admin, java.util.Collection<Long> ids) {
        checkAdmin(admin);
        return repository.findAllById(ids).stream()
                .collect(
                        java.util.stream.Collectors.toMap(
                                ServiceNotice::getId, ServiceNotice::getTitle));
    }

    @Transactional(readOnly = true)
    public AdminNotice adminDetail(LoginMember admin, Long id) {
        checkAdmin(admin);
        var n =
                repository
                        .findById(id)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return new AdminNotice(
                n.getId(),
                n.getTitle(),
                n.getContent(),
                n.getCreatedAt(),
                n.getDeletedAt(),
                n.isPolicyChange(),
                n.getEmailQueuedAt());
    }

    @Transactional(readOnly = true)
    public ServiceNoticeResponse get(Long id) {
        return ServiceNoticeResponse.from(find(id));
    }

    /** ① 등록하면 전체 회원에게 사이트 알림 (actor NULL) */
    @Transactional
    public Long create(LoginMember admin, ServiceNoticeRequest request) {
        checkAdmin(admin);
        validateEmailRequest(request);
        ServiceNotice notice =
                repository.save(
                        new ServiceNotice(
                                admin.getMemberId(),
                                request.title().trim(),
                                request.content().trim()));
        notice.setPolicyChange(request.policyChange());
        String message = notice.getTitle();
        for (Long memberId : memberService.findActiveMemberIds()) {
            notificationService.send(
                    memberId,
                    NotificationType.SERVICE_NOTICE,
                    null,
                    "SERVICE_NOTICE",
                    notice.getId(),
                    message,
                    "/service-notices/" + notice.getId());
        }
        queuePolicyEmail(notice, request);
        audit.record(
                admin.getMemberId(),
                "NOTICE_CREATE",
                "SERVICE_NOTICE",
                notice.getId(),
                request.sendEmail() ? "정책 변경 이메일 발송 요청" : null);
        return notice.getId();
    }

    @Transactional
    public Long update(LoginMember admin, Long id, ServiceNoticeRequest request) {
        checkAdmin(admin);
        validateEmailRequest(request);
        ServiceNotice notice = findForUpdate(id);
        if (notice.getEmailQueuedAt() != null && !request.policyChange()) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT, "이메일을 요청한 정책 변경 공지는 일반 공지로 변경할 수 없습니다");
        }
        notice.update(request.title().trim(), request.content().trim());
        notice.setPolicyChange(request.policyChange());
        queuePolicyEmail(notice, request);
        audit.record(
                admin.getMemberId(),
                "NOTICE_UPDATE",
                "SERVICE_NOTICE",
                notice.getId(),
                request.sendEmail() ? "정책 변경 이메일 발송 요청" : null);
        return notice.getId();
    }

    private void validateEmailRequest(ServiceNoticeRequest request) {
        if (request.sendEmail() && !request.policyChange()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "정책 변경 공지만 이메일을 발송할 수 있습니다");
        }
    }

    private void queuePolicyEmail(ServiceNotice notice, ServiceNoticeRequest request) {
        if (!request.sendEmail() || notice.getEmailQueuedAt() != null) return;
        LocalDateTime now = LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul"));
        for (Long id : memberService.findActiveMemberIds()) {
            memberService
                    .findActiveMember(id)
                    .filter(m -> m.getEmail() != null)
                    .ifPresent(
                            m ->
                                    emailOutboxService.enqueueNotification(
                                            m,
                                            NotificationType.POLICY_NOTICE,
                                            null,
                                            notice.getTitle(),
                                            "/service-notices/" + notice.getId(),
                                            now));
        }
        notice.markEmailQueued(now);
    }

    @Transactional
    public void delete(LoginMember admin, Long id) {
        checkAdmin(admin);
        findForUpdate(id).delete(LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul")));
        audit.record(admin.getMemberId(), "NOTICE_DELETE", "SERVICE_NOTICE", id, null);
    }

    private ServiceNotice find(Long id) {
        return repository
                .findById(id)
                .filter(n -> !n.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private ServiceNotice findForUpdate(Long id) {
        return repository
                .findForUpdate(id)
                .filter(n -> !n.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private void checkAdmin(LoginMember member) {
        if (member == null) throw new BusinessException(ErrorCode.NOT_ADMIN);
        var current = memberService.getActiveMember(member.getMemberId());
        if (!current.isAdmin() || current.isSuspended()) {
            throw new BusinessException(ErrorCode.NOT_ADMIN);
        }
    }
}
