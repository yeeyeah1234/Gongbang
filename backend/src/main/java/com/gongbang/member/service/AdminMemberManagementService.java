package com.gongbang.member.service;

import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.domain.*;
import com.gongbang.member.repository.MemberRepository;
import com.gongbang.notification.domain.NotificationType;
import com.gongbang.notification.service.NotificationService;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminMemberManagementService {
    private final MemberRepository members;
    private final AdminMemberService access;
    private final NotificationService notifications;
    private final EntityManager em;

    public record Item(
            Long id,
            String nickname,
            String email,
            MemberRole role,
            MemberStatus status,
            LoginType loginType,
            int loginFailCount,
            LocalDateTime lockedUntil,
            LocalDateTime suspendedUntil,
            int warningCount,
            boolean nicknameChangeRequired,
            String sanctionReason,
            LocalDateTime createdAt,
            LocalDateTime lastLoginAt) {
        static Item of(Member m) {
            return new Item(
                    m.getId(),
                    m.getNickname(),
                    m.getEmail(),
                    m.getRole(),
                    m.getStatus(),
                    m.getLoginType(),
                    m.getLoginFailCount(),
                    m.getLockedUntil(),
                    m.getSuspendedUntil(),
                    m.getWarningCount(),
                    m.isNicknameChangeRequired(),
                    m.getSanctionReason(),
                    m.getCreatedAt(),
                    m.getLastLoginAt());
        }
    }

    public record Action(String action, String reason, LocalDateTime until) {}

    @Transactional(readOnly = true)
    public Page<Item> list(Long actor, String keyword, String status, int page) {
        access.requireAdmin(actor);
        if (page < 0 || keyword.length() > 100)
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        var m = QMember.member;
        var where =
                keyword.isBlank()
                        ? m.id.isNotNull()
                        : m.email
                                .containsIgnoreCase(keyword)
                                .or(m.nickname.containsIgnoreCase(keyword));
        if (!status.isBlank()) {
            try {
                where = where.and(m.status.eq(MemberStatus.valueOf(status)));
            } catch (IllegalArgumentException ex) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
        }
        var q = new JPAQueryFactory(em);
        var rows =
                q.selectFrom(m)
                        .where(where)
                        .orderBy(m.id.desc())
                        .offset((long) page * 20)
                        .limit(20)
                        .fetch();
        Long total = q.select(m.count()).from(m).where(where).fetchOne();
        return new PageImpl<>(
                rows.stream().map(Item::of).toList(),
                PageRequest.of(page, 20),
                total == null ? 0 : total);
    }

    @Transactional
    public Item act(Long actor, Long id, Action request) {
        access.requireAdmin(actor);
        Member m =
                members.findForRoleUpdate(id)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!m.isActive()) throw new BusinessException(ErrorCode.INVALID_INPUT, "활성 회원만 처리할 수 있어요");
        String action = request.action();
        if ("UNLOCK".equals(action)) m.resetLoginFail();
        else if ("GRANT_ADMIN".equals(action))
            throw new BusinessException(ErrorCode.NOT_ADMIN, "관리자 권한은 다른 회원에게 부여할 수 없습니다");
        else {
            if (actor.equals(id))
                throw new BusinessException(ErrorCode.INVALID_INPUT, "자신에게 제재할 수 없어요");
            if (!java.util.Set.of("WARN", "SUSPEND", "UNSUSPEND", "REQUIRE_NICKNAME")
                    .contains(action == null ? "" : action))
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            String reason = request.reason() == null ? "" : request.reason().strip();
            var now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
            if (reason.isEmpty()
                    || reason.length() > 200
                    || ("SUSPEND".equals(action)
                            && (request.until() == null || !request.until().isAfter(now))))
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            m.moderate(action, reason, request.until(), actor, now);
            boolean nickname = "REQUIRE_NICKNAME".equals(action);
            notifications.send(
                    id,
                    nickname
                            ? NotificationType.NICKNAME_CHANGE_REQUIRED
                            : NotificationType.ACCOUNT_SANCTION,
                    null,
                    "MEMBER",
                    id,
                    (nickname ? "닉네임 변경이 필요해요: " : "계정 조치 안내 (" + action + "): ") + reason,
                    "/mypage");
        }
        return Item.of(m);
    }
}
