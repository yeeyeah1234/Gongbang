package com.gongbang.member.repository;

import com.gongbang.member.domain.Career;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CareerRepository extends JpaRepository<Career, Long> {

    /** MY-07 ④ 최근 날짜순 */
    List<Career> findByMemberIdOrderByStartDateDescIdDesc(Long memberId);

    long countByMemberId(Long memberId);

    void deleteByMemberId(Long memberId);
}
