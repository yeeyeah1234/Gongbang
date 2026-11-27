package com.gongbang.member.service;

import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.domain.AdminInvitation;
import com.gongbang.member.repository.AdminInvitationRepository;
import com.gongbang.member.repository.MemberRepository;
import com.gongbang.notification.domain.NotificationType;
import com.gongbang.notification.service.NotificationService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminInvitationService {
    private final com.gongbang.admin.service.AdminActivityWriter audit;
    private final AdminInvitationRepository invitations;
    private final MemberRepository members;
    private final AdminMemberService admins;
    private final NotificationService notifications;

    public record Summary(Long inviterId, Long inviteeId, String status) {}

    @Transactional(readOnly = true)
    public Summary summary(Long actor, Long id) {
        admins.requireAdmin(actor);
        var i =
                invitations
                        .findById(id)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return new Summary(i.getInviterId(), i.getInviteeId(), i.getStatus());
    }

    public record Result(Long invitationId, String status) {}

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }

    @Transactional
    public Result invite(Long actor, Long targetId) {
        admins.requireAdmin(actor);
        var target =
                members.findForRoleUpdate(targetId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!target.isActive()
                || target.isSuspended()
                || target.isNicknameChangeRequired()
                || target.isAdmin())
            throw new BusinessException(ErrorCode.INVALID_INPUT, "활성 일반 회원에게만 관리자 초대를 보낼 수 있어요");
        var pending = invitations.findByInviteeIdAndStatus(targetId, "PENDING");
        if (pending.isPresent())
            return new Result(pending.get().getId(), pending.get().getStatus());
        var invite = invitations.save(new AdminInvitation(actor, targetId, now()));
        notifications.send(
                targetId,
                NotificationType.ADMIN_INVITE,
                actor,
                "ADMIN_INVITATION",
                invite.getId(),
                "관리자 초대를 받았어요. 수락하면 다른 관리자와 동일한 관리 권한을 갖게 됩니다.",
                "/notifications");
        audit.record(actor, "ADMIN_INVITE", "MEMBER", targetId, null);
        return new Result(invite.getId(), invite.getStatus());
    }

    @Transactional
    public Result respond(Long memberId, Long id, boolean accept) {
        // 생성·수락·거절은 같은 수신자 행 잠금으로 직렬화한다.
        var target =
                members.findForRoleUpdate(memberId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        var invitation =
                invitations
                        .findById(id)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!memberId.equals(invitation.getInviteeId()))
            throw new BusinessException(ErrorCode.NOT_OWNER);
        if (!target.isActive() || target.isSuspended() || target.isNicknameChangeRequired())
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        String desired = accept ? "ACCEPTED" : "DECLINED";
        if (desired.equals(invitation.getStatus())) return new Result(id, desired);
        if (!"PENDING".equals(invitation.getStatus()))
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이미 처리된 관리자 초대입니다");
        if (accept) admins.grant(invitation.getInviterId(), memberId);
        invitation.respond(accept, now());
        audit.record(
                memberId,
                accept ? "ADMIN_INVITE_ACCEPT" : "ADMIN_INVITE_DECLINE",
                "ADMIN_INVITATION",
                id,
                null);
        return new Result(id, invitation.getStatus());
    }

    @Transactional(readOnly = true)
    public Map<Long, String> findStatuses(Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return invitations.findAllById(ids).stream()
                .collect(Collectors.toMap(AdminInvitation::getId, AdminInvitation::getStatus));
    }
}
