package com.gongbang.member.repository;

import com.gongbang.member.domain.AdminInvitation;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminInvitationRepository extends JpaRepository<AdminInvitation, Long> {
    Optional<AdminInvitation> findByInviteeIdAndStatus(Long inviteeId, String status);
}
