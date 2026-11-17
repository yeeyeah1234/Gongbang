package com.gongbang.team.repository;

import com.gongbang.team.domain.Team;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeamRepository extends JpaRepository<Team, Long> {

    Optional<Team> findByPostId(Long postId);

    boolean existsByPostId(Long postId);

    /**
     * 팀원을 추가할 때 팀 행을 잠근다 (SELECT … FOR UPDATE, AGENTS.md 7). 같은 팀에 동시에 들어와도 색 번호가 겹치지 않게 한다. 잠금은 그
     * 트랜잭션이 끝날 때 풀린다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Team t where t.id = :teamId")
    Optional<Team> findByIdForUpdate(@Param("teamId") Long teamId);
}
