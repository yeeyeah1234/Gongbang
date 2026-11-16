package com.gongbang.member.repository;

import com.gongbang.member.domain.VerificationRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationRequestRepository extends JpaRepository<VerificationRequest, Long> {

    void deleteByMemberId(Long memberId);
}
