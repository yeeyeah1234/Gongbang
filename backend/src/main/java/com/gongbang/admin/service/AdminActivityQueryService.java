package com.gongbang.admin.service;

import com.gongbang.admin.repository.AdminActivityRepository;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.service.AdminMemberService;
import com.gongbang.member.service.MemberService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminActivityQueryService {
    private final AdminActivityRepository repository;
    private final AdminMemberService admins;
    private final MemberService members;

    public record Item(
            Long id,
            Long actorId,
            String actorNickname,
            String action,
            String targetType,
            Long targetId,
            String reason,
            LocalDateTime createdAt) {}

    @Transactional(readOnly = true)
    public Page<Item> list(Long actor, String action, int page) {
        admins.requireAdmin(actor);
        if (page < 0 || action.length() > 40) throw new BusinessException(ErrorCode.INVALID_INPUT);
        var pageable =
                PageRequest.of(
                        page, 20, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        var rows =
                action.isBlank()
                        ? repository.findAll(pageable)
                        : repository.findByAction(action, pageable);
        return rows.map(
                r ->
                        new Item(
                                r.getId(),
                                r.getActorId(),
                                members.displayName(r.getActorId()),
                                r.getAction(),
                                r.getTargetType(),
                                r.getTargetId(),
                                r.getReason(),
                                r.getCreatedAt()));
    }
}
