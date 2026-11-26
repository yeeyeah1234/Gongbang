package com.gongbang.invitation.repository;

import com.gongbang.invitation.domain.InvitationStatus;
import com.gongbang.invitation.domain.TeamInvitation;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeamInvitationRepository extends JpaRepository<TeamInvitation, Long> {

    /** 초대 수락 시 잠금 순서(recruit_post → team_invitation)를 지키기 위해 엔티티를 1차 캐시에 올리지 않고 핵심 메타만 읽는다. */
    interface InvitationMeta {
        Long getPostId();

        Long getInviteeId();

        InvitationStatus getStatus();
    }

    @Query(
            "select i.postId as postId, i.inviteeId as inviteeId, i.status as status"
                    + " from TeamInvitation i where i.id = :id")
    Optional<InvitationMeta> findMetaById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from TeamInvitation i where i.id = :id")
    Optional<TeamInvitation> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select i from TeamInvitation i"
                    + " where i.postId = :postId and i.inviteeId = :inviteeId")
    Optional<TeamInvitation> findByPostIdAndInviteeIdForUpdate(
            @Param("postId") Long postId, @Param("inviteeId") Long inviteeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from TeamInvitation i where i.postId = :postId and i.status = :status")
    List<TeamInvitation> findByPostIdAndStatusForUpdate(
            @Param("postId") Long postId, @Param("status") InvitationStatus status);

    List<TeamInvitation> findByIdIn(Collection<Long> ids);
}
