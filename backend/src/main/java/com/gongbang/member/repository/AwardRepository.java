package com.gongbang.member.repository;

import com.gongbang.member.domain.Award;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AwardRepository extends JpaRepository<Award, Long> {

    List<Award> findByMemberIdOrderByAwardedDateDescIdDesc(Long memberId);

    void deleteByMemberId(Long memberId);
}
