package com.gongbang.member.repository;

import com.gongbang.member.domain.Certificate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CertificateRepository extends JpaRepository<Certificate, Long> {

    List<Certificate> findByMemberIdOrderByAcquiredDateDescIdDesc(Long memberId);

    void deleteByMemberId(Long memberId);
}
