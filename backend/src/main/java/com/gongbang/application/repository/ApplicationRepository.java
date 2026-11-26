package com.gongbang.application.repository;

import com.gongbang.application.domain.Application;
import com.gongbang.application.domain.ApplicationStatus;
import com.gongbang.application.dto.MyApplicationListItem;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApplicationRepository extends JpaRepository<Application, Long> {
    @Query(
            value =
                    """
            SELECT a.id AS applicationId, a.post_id AS postId,
                   CASE WHEN p.deleted_at IS NULL THEN p.title ELSE '삭제된 모집글' END AS title,
                   a.status AS status, p.deleted_at AS deletedAt, a.created_at AS createdAt
            FROM application a JOIN recruit_post p ON p.id = a.post_id
            WHERE a.applicant_id = :memberId
            ORDER BY a.created_at DESC, a.id DESC
            """,
            countQuery =
                    """
            SELECT COUNT(*) FROM application a JOIN recruit_post p ON p.id = a.post_id
            WHERE a.applicant_id = :memberId
            """,
            nativeQuery = true)
    Page<MyApplicationListItem> findMine(@Param("memberId") Long memberId, Pageable pageable);

    @org.springframework.data.jpa.repository.Query(
            "select distinct a.applicantId from Application a where a.postId = :postId")
    java.util.List<Long> findApplicantIdsForAdmin(
            @org.springframework.data.repository.query.Param("postId") Long postId);

    /** REC-001 ③: 거절된 지원도 있으면 다시 지원할 수 없다. */
    boolean existsByPostIdAndApplicantId(Long postId, Long applicantId);

    Optional<Application> findByPostIdAndApplicantId(Long postId, Long applicantId);

    /** INT-005 ③: 초대 수락 때 그 회원의 이 글 지원을 잠가 읽는다 (모집글 행을 잠근 뒤에 부른다). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select a from Application a"
                    + " where a.postId = :postId and a.applicantId = :applicantId")
    Optional<Application> findByPostIdAndApplicantIdForUpdate(
            @Param("postId") Long postId, @Param("applicantId") Long applicantId);

    /**
     * REC-004: 모집글을 먼저 잠그려고 지원의 모집글 ID만 값으로 읽는다. 엔티티를 읽지 않으므로 뒤의 잠금 조회(findByIdForUpdate)가 옛 값을
     * 돌려주지 않는다.
     */
    @Query("select a.postId from Application a where a.id = :applicationId")
    Optional<Long> findPostIdById(@Param("applicationId") Long applicationId);

    /** REC-003 ④: 한 탭의 지원자를 먼저 지원한 순으로 (같은 시각이면 먼저 들어온 행이 먼저). */
    List<Application> findByPostIdAndStatusOrderByCreatedAtAscIdAsc(
            Long postId, ApplicationStatus status);

    /** REC-003 ②: 상태별 지원 수. [status, count] 행 목록. 없는 상태는 행이 없다. */
    @Query(
            "select a.status, count(a) from Application a"
                    + " where a.postId = :postId group by a.status")
    List<Object[]> countByStatus(@Param("postId") Long postId);

    /**
     * 지원 상태를 바꾸는 처리(REC-002 취소, REC-004 수락, REC-005 거절)에서 지원 행을 잠근다 (SELECT … FOR UPDATE). 취소와
     * 수락·거절이 동시에 들어와도 한쪽만 성공한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Application a where a.id = :applicationId")
    Optional<Application> findByIdForUpdate(@Param("applicationId") Long applicationId);

    /**
     * 한 글의 특정 상태 지원을 모두 잠근다 (PST-004 ④ 종료, REC-006 ④ 자동 거절). 잠근 뒤 바꾸므로 그 사이 지원자가 취소해도 엇갈리지 않는다(취소는
     * 이 처리가 끝날 때까지 기다렸다가 「이미 처리된 지원」을 받는다).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Application a where a.postId = :postId and a.status = :status")
    List<Application> findByPostIdAndStatusForUpdate(
            @Param("postId") Long postId, @Param("status") ApplicationStatus status);
}
