package com.gongbang.application.repository;

import com.gongbang.application.domain.Application;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    /** REC-001 ③: 거절된 지원도 있으면 다시 지원할 수 없다. */
    boolean existsByPostIdAndApplicantId(Long postId, Long applicantId);

    Optional<Application> findByPostIdAndApplicantId(Long postId, Long applicantId);
}
