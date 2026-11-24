package com.gongbang.member.repository;

import com.gongbang.member.domain.Certificate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CertificateRepository extends JpaRepository<Certificate, Long> {

    @org.springframework.data.jpa.repository.Lock(
            jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from Certificate c where c.id = :id")
    java.util.Optional<Certificate> findForUpdate(
            @org.springframework.data.repository.query.Param("id") Long id);

    List<Certificate> findByMemberIdOrderByAcquiredDateDescIdDesc(Long memberId);

    void deleteByMemberId(Long memberId);
}
