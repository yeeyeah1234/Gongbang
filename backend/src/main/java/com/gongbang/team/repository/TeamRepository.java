package com.gongbang.team.repository;

import com.gongbang.team.domain.Team;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeamRepository extends JpaRepository<Team, Long> {

    Optional<Team> findByPostId(Long postId);

    boolean existsByPostId(Long postId);

    boolean existsByIdAndDeletedAtIsNull(Long id);

    boolean existsByIdAndDeletedAtIsNotNull(Long id);

    Optional<Team> findByIdAndDeletedAtIsNull(Long id);

    /** WS-08 ⑨ 팀 삭제 사전 확인용 스칼라 프로젝션 (1차 캐시에 Team 엔티티를 올리지 않는다). */
    interface ActiveTeamMeta {
        Long getId();

        Long getPostId();
    }

    @Query(
            "select t.id as id, t.postId as postId from Team t"
                    + " where t.id = :teamId and t.deletedAt is null")
    Optional<ActiveTeamMeta> findActiveMeta(@Param("teamId") Long teamId);

    /**
     * 팀원을 추가하거나 삭제·수정할 때 삭제되지 않은 팀 행을 잠근다 (SELECT … FOR UPDATE, AGENTS.md 7). 잠금은 그 트랜잭션이 끝날 때 풀린다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Team t where t.id = :teamId and t.deletedAt is null")
    Optional<Team> findByIdForUpdate(@Param("teamId") Long teamId);

    /**
     * WS-02 워크스페이스 헤더: 팀 이름, 공모전 ID·공모전명, 마감일. 팀이 없으면 빈 값.
     *
     * <ul>
     *   <li>공모전 ID는 rp.contest_id 그대로다. 직접 입력한 외부 공모전이나 모집글이 없으면 NULL (CAL 자동 일정 상세의 「공모전 상세 보기」
     *       링크용).
     *   <li>모집글에 contest_id가 있으면 contest.title·end_date, 없으면
     *       recruit_post.ext_contest_title·ext_deadline (CAL-06 {@code CalendarEventRepository}와 같은
     *       기준).
     *   <li>recruit_post를 deleted_at으로 거르지 않는다: 모집글이 삭제돼도 팀은 남는다 (PST-004 ④).
     *   <li>모집글 행이 없어도(team.post_id는 FK 없음) 팀 이름은 돌려주고 공모전명·마감일은 null.
     * </ul>
     *
     * <p>post·contest 도메인의 repository를 부르지 않으려고 native 쿼리 하나로 읽는다 (AGENTS.md 3).
     */
    @Query(
            value =
                    "SELECT t.name AS teamName,"
                            + " rp.contest_id AS contestId,"
                            + " CASE WHEN rp.contest_id IS NOT NULL THEN c.title"
                            + " ELSE rp.ext_contest_title END AS contestTitle,"
                            + " CASE WHEN rp.contest_id IS NOT NULL THEN c.end_date"
                            + " ELSE rp.ext_deadline END AS deadline"
                            + " FROM team t"
                            + " LEFT JOIN recruit_post rp ON rp.id = t.post_id"
                            + " LEFT JOIN contest c ON c.id = rp.contest_id"
                            + " WHERE t.id = :teamId",
            nativeQuery = true)
    Optional<TeamWorkspaceSummary> findWorkspaceSummary(@Param("teamId") Long teamId);

    /**
     * WS-08 워크스페이스 팀 목록: 내가 현재 팀원(left_at IS NULL)인 팀. 공모전명·마감일 기준은 {@link #findWorkspaceSummary}와
     * 같다. 정렬은 service가 진행 중·종료로 나눈 뒤 한다.
     */
    @Query(
            value =
                    "SELECT t.id AS teamId,"
                            + " t.name AS teamName,"
                            + " CASE WHEN rp.contest_id IS NOT NULL THEN c.title"
                            + " ELSE rp.ext_contest_title END AS contestTitle,"
                            + " CASE WHEN rp.contest_id IS NOT NULL THEN c.end_date"
                            + " ELSE rp.ext_deadline END AS deadline,"
                            + " t.last_activity_at AS lastActivityAt,"
                            + " tm.role AS myRole"
                            + " FROM team_member tm"
                            + " JOIN team t ON t.id = tm.team_id"
                            + " LEFT JOIN recruit_post rp ON rp.id = t.post_id"
                            + " LEFT JOIN contest c ON c.id = rp.contest_id"
                            + " WHERE tm.member_id = :memberId AND tm.left_at IS NULL AND t.deleted_at IS NULL",
            nativeQuery = true)
    List<MyTeamRow> findMyTeams(@Param("memberId") Long memberId);
}
