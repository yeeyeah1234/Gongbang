package com.gongbang.team.repository;

import com.gongbang.team.domain.TeamMember;
import com.gongbang.team.domain.TeamRole;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {

    /** 현재 팀원인지 (left_at IS NULL). */
    boolean existsByTeamIdAndMemberIdAndLeftAtIsNull(Long teamId, Long memberId);

    /** 현재 팀원이면서 그 역할인지. */
    boolean existsByTeamIdAndMemberIdAndRoleAndLeftAtIsNull(
            Long teamId, Long memberId, TeamRole role);

    /** 나간 기록이 하나라도 있는지 (leave_type은 보지 않는다, PST-006 ⑨). */
    boolean existsByTeamIdAndMemberIdAndLeftAtIsNotNull(Long teamId, Long memberId);

    /** 현재 팀원들이 쓰고 있는 색 번호. */
    @Query(
            "select tm.colorIndex from TeamMember tm where tm.team.id = :teamId and tm.leftAt is null")
    List<Integer> findActiveColorIndexes(@Param("teamId") Long teamId);
}
