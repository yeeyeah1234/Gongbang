package com.gongbang.member.repository;

import com.gongbang.member.domain.Member;
import com.gongbang.member.domain.MemberStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByEmail(String email);

    Optional<Member> findByNickname(String nickname);

    Optional<Member> findByKakaoId(String kakaoId);

    Optional<Member> findByGoogleId(String googleId);

    boolean existsByNickname(String nickname);

    List<Member> findByStatusAndWithdrawnAtBefore(MemberStatus status, LocalDateTime before);

    @Query("select m.id from Member m where m.status = :status")
    List<Long> findIdsByStatus(@Param("status") MemberStatus status);

    /**
     * 관심 분야 코드 → category.id. category는 최규선 담당 테이블이라 값만 읽는다. TODO(확인 필요): contest 도메인에 code→id 조회
     * service가 생기면 그걸 부르도록 바꾼다 (AGENTS 3).
     */
    @Query(value = "select id from category where code in (:codes)", nativeQuery = true)
    List<Long> findCategoryIdsByCodes(@Param("codes") Collection<String> codes);

    @Query(
            value = "select code from category where id in (:ids) order by sort_order",
            nativeQuery = true)
    List<String> findCategoryCodesByIds(@Param("ids") Collection<Long> ids);
}
