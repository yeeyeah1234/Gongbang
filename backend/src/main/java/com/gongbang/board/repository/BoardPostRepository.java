package com.gongbang.board.repository;

import com.gongbang.board.domain.BoardCategory;
import com.gongbang.board.domain.BoardPost;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BoardPostRepository extends JpaRepository<BoardPost, Long> {

    /** BRD-001 목록: 삭제되지 않은 글. 말머리·공모전은 null이면 거르지 않는다. 정렬은 Pageable로 준다. */
    @Query(
            "select p from BoardPost p where p.deletedAt is null"
                    + " and (:category is null or p.category = :category)"
                    + " and (:contestId is null or p.contestId = :contestId)")
    Page<BoardPost> findActive(
            @Param("category") BoardCategory category,
            @Param("contestId") Long contestId,
            Pageable pageable);

    /** BRD-008 인기글: since 이후에 쓴 글 중 좋아요 1개 이상을 좋아요 → 조회수 → 최신 순으로. 개수는 Pageable로 자른다. */
    @Query(
            "select p from BoardPost p where p.deletedAt is null and p.likeCount >= 1"
                    + " and p.createdAt >= :since"
                    + " and (:category is null or p.category = :category)"
                    + " order by p.likeCount desc, p.viewCount desc, p.createdAt desc, p.id desc")
    List<BoardPost> findPopular(
            @Param("category") BoardCategory category,
            @Param("since") LocalDateTime since,
            Pageable pageable);

    /** 수정·삭제·좋아요에서 게시글 행을 잠근다 (SELECT … FOR UPDATE, AGENTS.md 7). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from BoardPost p where p.id = :boardPostId")
    Optional<BoardPost> findByIdForUpdate(@Param("boardPostId") Long boardPostId);

    /** BRD-004 ⑤: 마지막 조회가 threshold 이전(24시간 지남)이면 시각을 갱신한다. 갱신한 행 수(0 또는 1). */
    @Modifying
    @Query(
            value =
                    "UPDATE board_post_view SET last_viewed_at = :now"
                            + " WHERE board_post_id = :boardPostId AND member_id = :memberId"
                            + " AND last_viewed_at <= :threshold",
            nativeQuery = true)
    int touchStaleView(
            @Param("boardPostId") Long boardPostId,
            @Param("memberId") Long memberId,
            @Param("now") LocalDateTime now,
            @Param("threshold") LocalDateTime threshold);

    /** BRD-004 ⑤: 처음 보는 회원이면 기록을 넣는다. 넣었으면 1, 이미 있으면 0 (동시 요청도 하나만 1). */
    @Modifying
    @Query(
            value =
                    "INSERT IGNORE INTO board_post_view (board_post_id, member_id, last_viewed_at)"
                            + " VALUES (:boardPostId, :memberId, :now)",
            nativeQuery = true)
    int insertViewIfAbsent(
            @Param("boardPostId") Long boardPostId,
            @Param("memberId") Long memberId,
            @Param("now") LocalDateTime now);

    /** BRD-004 ⑤: 동시에 열어도 빠지지 않게 DB에서 바로 1 올린다. */
    @Modifying
    @Query("update BoardPost p set p.viewCount = p.viewCount + 1 where p.id = :boardPostId")
    int increaseViewCount(@Param("boardPostId") Long boardPostId);

    /** BRD-007 좋아요: 넣었으면 1, 이미 눌렀으면 0. 부르는 쪽이 게시글 행을 잠근 뒤 부른다. */
    @Modifying
    @Query(
            value =
                    "INSERT IGNORE INTO board_post_like (board_post_id, member_id)"
                            + " VALUES (:boardPostId, :memberId)",
            nativeQuery = true)
    int insertLike(@Param("boardPostId") Long boardPostId, @Param("memberId") Long memberId);

    /** BRD-007 좋아요 취소: 지웠으면 1, 안 눌렀으면 0. */
    @Modifying
    @Query(
            value =
                    "DELETE FROM board_post_like WHERE board_post_id = :boardPostId"
                            + " AND member_id = :memberId",
            nativeQuery = true)
    int deleteLike(@Param("boardPostId") Long boardPostId, @Param("memberId") Long memberId);

    @Query(
            value =
                    "SELECT COUNT(*) FROM board_post_like WHERE board_post_id = :boardPostId"
                            + " AND member_id = :memberId",
            nativeQuery = true)
    long countLike(@Param("boardPostId") Long boardPostId, @Param("memberId") Long memberId);
}
