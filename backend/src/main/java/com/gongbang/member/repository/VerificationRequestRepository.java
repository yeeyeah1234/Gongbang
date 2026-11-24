package com.gongbang.member.repository;

import com.gongbang.member.domain.VerificationRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationRequestRepository extends JpaRepository<VerificationRequest, Long> {

    java.util.Optional<VerificationRequest>
            findFirstByMemberIdAndTargetTypeAndTargetIdOrderByIdDesc(
                    Long memberId, VerificationRequest.TargetType targetType, Long targetId);

    org.springframework.data.domain.Page<VerificationRequest> findByStatus(
            com.gongbang.member.domain.VerifyStatus status,
            org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Lock(
            jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query(
            "select v from VerificationRequest v where v.id = :id")
    java.util.Optional<VerificationRequest> findForReview(
            @org.springframework.data.repository.query.Param("id") Long id);

    void deleteByMemberId(Long memberId);
}
