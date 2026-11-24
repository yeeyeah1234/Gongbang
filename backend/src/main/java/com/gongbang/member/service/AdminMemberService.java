package com.gongbang.member.service;

import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.domain.*;
import com.gongbang.member.repository.MemberRepository;
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
    private final MemberRepository repository;

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
            log.info("Admin role granted: actorId={}, memberId={}", actorId, memberId);
        }
        return Item.from(target);
    }
}
