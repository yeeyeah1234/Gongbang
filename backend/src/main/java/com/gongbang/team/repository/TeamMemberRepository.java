package com.gongbang.team.repository;

import com.gongbang.team.domain.TeamMember;
import com.gongbang.team.domain.TeamRole;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {

    /** 공지 알림 수신자: 팀장과 나간 팀원을 제외한다. */
    @Query(
            "select distinct tm.memberId from TeamMember tm"
                    + " where tm.team.id = :teamId and tm.leftAt is null and tm.role = :role")
    List<Long> findActiveMemberIdsByRole(
            @Param("teamId") Long teamId, @Param("role") TeamRole role);

    /** 현재 팀원인지 (left_at IS NULL). */
    boolean existsByTeamIdAndMemberIdAndLeftAtIsNull(Long teamId, Long memberId);

    /** 삭제되지 않은 팀의 현재 팀원인지 (left_at IS NULL AND team.deleted_at IS NULL). */
    boolean existsByTeamIdAndTeamDeletedAtIsNullAndMemberIdAndLeftAtIsNull(
            Long teamId, Long memberId);

    /** 현재 팀원이면서 그 역할인지. */
    boolean existsByTeamIdAndMemberIdAndRoleAndLeftAtIsNull(
            Long teamId, Long memberId, TeamRole role);

    /** 삭제되지 않은 팀의 현재 팀원이면서 그 역할인지. */
    boolean existsByTeamIdAndTeamDeletedAtIsNullAndMemberIdAndRoleAndLeftAtIsNull(
            Long teamId, Long memberId, TeamRole role);

    /** 활성 팀원 수 (사전 확인용 스칼라 조회, WS-08 ⑨). */
    long countByTeamIdAndLeftAtIsNull(Long teamId);

    /** 나간 기록이 하나라도 있는지 (leave_type은 보지 않는다, PST-006 ⑨). */
    boolean existsByTeamIdAndMemberIdAndLeftAtIsNotNull(Long teamId, Long memberId);

    /** 현재 팀원 목록 (joinedAt 오름차순). */
    List<TeamMember> findAllByTeamIdAndLeftAtIsNullOrderByJoinedAtAscIdAsc(Long teamId);

    /** WS-08 ⑨ 팀 삭제: 현재 활성 팀원 행을 배타 잠금(FOR UPDATE)으로 조회해 스냅샷 읽기를 방지한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select tm from TeamMember tm where tm.team.id = :teamId and tm.leftAt is null")
    List<TeamMember> findAllByTeamIdAndLeftAtIsNullForUpdate(@Param("teamId") Long teamId);

    /** WS-08 팀 목록: 여러 팀의 현재 팀원을 한 번에 (팀 안에서는 입장 순). */
    List<TeamMember> findAllByTeamIdInAndLeftAtIsNullOrderByJoinedAtAscIdAsc(
            Collection<Long> teamIds);

    /** 특정 회원의 현재 팀원 행 조회. */
    Optional<TeamMember> findByTeamIdAndMemberIdAndLeftAtIsNull(Long teamId, Long memberId);

    /** 현재 팀원의 역할만 스칼라로 조회한다(1차 캐시에 엔티티를 올리지 않기 위해 프로젝션 사용, WS-09). */
    @Query(
            "select tm.role from TeamMember tm"
                    + " where tm.team.id = :teamId and tm.memberId = :memberId and tm.leftAt is null")
    Optional<TeamRole> findActiveRole(
            @Param("teamId") Long teamId, @Param("memberId") Long memberId);

    /** 특정 회원의 현재 팀원 행을 배타 잠금(FOR UPDATE)으로 조회해 최신 커밋 상태를 확인한다 (WS-09). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select tm from TeamMember tm"
                    + " where tm.team.id = :teamId and tm.memberId = :memberId and tm.leftAt is null")
    Optional<TeamMember> findByTeamIdAndMemberIdAndLeftAtIsNullForUpdate(
            @Param("teamId") Long teamId, @Param("memberId") Long memberId);

    /** 현재 팀원들이 쓰고 있는 색 번호. */
    @Query(
            "select tm.colorIndex from TeamMember tm where tm.team.id = :teamId and tm.leftAt is null")
    List<Integer> findActiveColorIndexes(@Param("teamId") Long teamId);
}
