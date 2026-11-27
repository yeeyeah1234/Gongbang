package com.gongbang.member.repository;

import com.gongbang.member.domain.AdminInvitation;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface AdminInvitationRepository extends JpaRepository<AdminInvitation, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AdminInvitation> findByInviteeIdAndStatus(Long inviteeId, String status);
}
