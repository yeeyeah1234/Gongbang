package com.gongbang.contest.repository;

import com.gongbang.contest.domain.Contest;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContestRepository extends JpaRepository<Contest, Long> {

    Optional<Contest> findBySourceUrl(String sourceUrl);

    @Query("select c from Contest c join fetch c.category where c.id = :id")
    Optional<Contest> findWithCategoryById(@Param("id") Long id);

    /** 다른 도메인 목록(PST-001 등)에서 공모전 여러 개를 분야와 함께 한 번에 읽는다. */
    @Query("select c from Contest c join fetch c.category where c.id in :ids")
    List<Contest> findAllWithCategoryByIdIn(@Param("ids") Collection<Long> ids);

    /** MAIN-02 ⑤: 동시에 열어도 빠지지 않게 DB에서 바로 1 올린다. 바뀐 행 수(없으면 0)를 돌려준다. */
    @Modifying(clearAutomatically = true)
    @Query("update Contest c set c.viewCount = c.viewCount + 1 where c.id = :id")
    int increaseViewCount(@Param("id") Long id);
}
