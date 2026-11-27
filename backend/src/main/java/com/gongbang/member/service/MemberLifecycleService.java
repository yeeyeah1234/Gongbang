package com.gongbang.member.service;

import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.domain.Member;
import com.gongbang.member.repository.MemberRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 팀 가입·생성·위임과 회원 탈퇴 사이의 상태 변경을 직렬화한다. */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class MemberLifecycleService {
    private final MemberRepository members;
    private final EntityManager em;

    public void lockActiveMembers(Collection<Long> ids) {
        var sorted = ids.stream().distinct().sorted().toList();
        if (sorted.isEmpty()) return;
        var statuses = members.findStatusesForUpdate(sorted);
        if (statuses.size() != sorted.size()
                || statuses.stream().anyMatch(s -> !"ACTIVE".equals(s)))
            throw new BusinessException(ErrorCode.INVALID_INPUT, "탈퇴한 회원은 팀에 참여할 수 없어요.");
    }

    public Member activeForWithdrawal(Long id) {
        var member =
                members.findForRoleUpdate(id)
                        .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        em.refresh(member, LockModeType.PESSIMISTIC_WRITE);
        if (!member.isActive()) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return member;
    }
}
