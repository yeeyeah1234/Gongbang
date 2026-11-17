package com.gongbang.post.repository;

import com.gongbang.post.domain.RecruitPost;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecruitPostRepository extends JpaRepository<RecruitPost, Long> {

    /** PST-001: 삭제되지 않은 모집글 (PST-004 소프트 삭제 제외). */
    Page<RecruitPost> findByDeletedAtIsNull(Pageable pageable);

    /**
     * 목표·확정 인원을 바꾸는 처리(PST-003 수정, REC-004 수락 등)에서 모집글 행을 잠근다 (SELECT … FOR UPDATE,
     * AGENTS.md 7).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from RecruitPost p where p.id = :postId")
    Optional<RecruitPost> findByIdForUpdate(@Param("postId") Long postId);

    /**
     * PST-005 ⑥: 마지막 조회가 threshold 이전(24시간 지남)이면 시각을 갱신한다. 갱신한 행 수(0 또는 1)를 돌려준다.
     *
     * <p>조건을 WHERE에 두어 동시에 들어와도 한 요청만 1을 받는다 (행 잠금).
     */
    @Modifying
    @Query(
            value =
                    "UPDATE post_view SET last_viewed_at = :now"
                            + " WHERE post_id = :postId AND member_id = :memberId"
                            + " AND last_viewed_at <= :threshold",
            nativeQuery = true)
    int touchStaleView(
            @Param("postId") Long postId,
            @Param("memberId") Long memberId,
            @Param("now") LocalDateTime now,
            @Param("threshold") LocalDateTime threshold);

    /** PST-005 ⑥: 처음 보는 회원이면 기록을 넣는다. 넣었으면 1, 이미 있으면 0 (동시 요청도 하나만 1). */
    @Modifying
    @Query(
            value =
                    "INSERT IGNORE INTO post_view (post_id, member_id, last_viewed_at)"
                            + " VALUES (:postId, :memberId, :now)",
            nativeQuery = true)
    int insertViewIfAbsent(
            @Param("postId") Long postId,
            @Param("memberId") Long memberId,
            @Param("now") LocalDateTime now);

    /** PST-005 ⑥: 동시에 열어도 빠지지 않게 DB에서 바로 1 올린다. */
    @Modifying
    @Query("update RecruitPost p set p.viewCount = p.viewCount + 1 where p.id = :postId")
    int increaseViewCount(@Param("postId") Long postId);
}
