package com.gongbang.application.repository;

import com.gongbang.application.domain.Application;
import com.gongbang.application.domain.ApplicationStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    /** REC-001 ③: 거절된 지원도 있으면 다시 지원할 수 없다. */
    boolean existsByPostIdAndApplicantId(Long postId, Long applicantId);

    Optional<Application> findByPostIdAndApplicantId(Long postId, Long applicantId);

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
}
