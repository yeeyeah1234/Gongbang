package com.gongbang.member.service;

import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.domain.*;
import com.gongbang.member.repository.MemberRepository;
import com.gongbang.notification.domain.NotificationType;
import com.gongbang.notification.service.NotificationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminMemberService {
    private final com.gongbang.admin.service.AdminActivityWriter audit;
    private final MemberRepository repository;
    private final NotificationService notificationService;

    public record Item(Long memberId, String nickname, String email, MemberRole role) {
        static Item from(Member m) {
            return new Item(m.getId(), m.getNickname(), m.getEmail(), m.getRole());
        }
    }

    public record Result(List<Item> content, long totalElements, int totalPages, int number) {}

    public void requireAdmin(Long id) {
        Member actor =
                repository
                        .findById(id)
                        .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (!actor.isActive() || actor.isSuspended() || actor.getRole() != MemberRole.ADMIN)
            throw new BusinessException(ErrorCode.NOT_ADMIN);
    }

    @Transactional(readOnly = true)
    public boolean isAdmin(Long id) {
        if (id == null) {
            return false;
        }
        return repository
                .findById(id)
                .filter(
                        actor ->
                                actor.isActive()
                                        && !actor.isSuspended()
                                        && actor.getRole() == MemberRole.ADMIN)
                .isPresent();
    }

    @Transactional(readOnly = true)
    public Result list(Long actorId, String keyword, int page) {
        requireAdmin(actorId);
        if (page < 0 || keyword == null || keyword.length() > 100)
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        var result =
                repository.searchActiveForAdmin(
                        keyword.strip(), PageRequest.of(page, 20, Sort.by("id").descending()));
        return new Result(
                result.getContent().stream().map(Item::from).toList(),
                result.getTotalElements(),
                result.getTotalPages(),
                page);
    }

    @Transactional
    public Item grant(Long actorId, Long memberId) {
        requireAdmin(actorId);
        Member target =
                repository
                        .findForRoleUpdate(memberId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!target.isActive() || target.isSuspended())
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        if (target.getRole() != MemberRole.ADMIN) {
            target.grantAdmin();
            audit.record(actorId, "ADMIN_GRANTED", "MEMBER", memberId, null);
            // 권한 변경 결과를 시스템 알림으로 보내 부여한 관리자도 수신한다.
            for (Member admin :
                    repository.findByRoleAndStatus(MemberRole.ADMIN, MemberStatus.ACTIVE)) {
                if (memberId.equals(admin.getId()) || admin.isSuspended()) continue;
                notificationService.send(
                        admin.getId(),
                        NotificationType.ADMIN_GRANTED,
                        null,
                        "MEMBER",
                        memberId,
                        "@" + target.getNickname() + " 님이 관리자가 되었습니다!",
                        "/admin/members");
            }
            log.info("Admin role granted: actorId={}, memberId={}", actorId, memberId);
        }
        return Item.from(target);
    }
}
