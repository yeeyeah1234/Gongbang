package com.gongbang.auth.repository;

import com.gongbang.auth.domain.EmailVerification;
import com.gongbang.auth.domain.VerificationPurpose;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

    /** AUTH-05 ③ 같은 이메일 1시간 5번 = created_at으로 센다 */
    long countByEmailAndPurposeAndCreatedAtAfter(
            String email, VerificationPurpose purpose, LocalDateTime after);

    Optional<EmailVerification> findTopByEmailAndPurposeOrderByIdDesc(
            String email, VerificationPurpose purpose);
}
