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

    private final ServiceNoticeRepository repository;
    private final NotificationService notificationService;
    private final MemberService memberService;

    @Transactional(readOnly = true)
    public ServiceNoticeResponse get(Long id) {
        return ServiceNoticeResponse.from(find(id));
    }

    /** ① 등록하면 전체 회원에게 사이트 알림 (actor NULL) */
    @Transactional
    public Long create(LoginMember admin, ServiceNoticeRequest request) {
        checkAdmin(admin);
        ServiceNotice notice =
                repository.save(
                        new ServiceNotice(
                                admin.getMemberId(),
                                request.title().trim(),
                                request.content().trim()));
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
        return notice.getId();
    }

    @Transactional
    public Long update(LoginMember admin, Long id, ServiceNoticeRequest request) {
        checkAdmin(admin);
        ServiceNotice notice = find(id);
        notice.update(request.title().trim(), request.content().trim());
        return notice.getId();
    }

    @Transactional
    public void delete(LoginMember admin, Long id) {
        checkAdmin(admin);
        find(id).delete(LocalDateTime.now());
    }

    private ServiceNotice find(Long id) {
        return repository
                .findById(id)
                .filter(n -> !n.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private static void checkAdmin(LoginMember member) {
        if (member == null || !member.isAdmin()) {
            throw new BusinessException(ErrorCode.NOT_ADMIN);
        }
    }
}
