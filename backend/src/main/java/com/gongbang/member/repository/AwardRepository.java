package com.gongbang.member.repository;

import com.gongbang.member.domain.Award;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AwardRepository extends JpaRepository<Award, Long> {

    @org.springframework.data.jpa.repository.Lock(
            jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select a from Award a where a.id = :id")
    java.util.Optional<Award> findForUpdate(
            @org.springframework.data.repository.query.Param("id") Long id);

    List<Award> findByMemberIdOrderByAwardedDateDescIdDesc(Long memberId);

    void deleteByMemberId(Long memberId);
}
