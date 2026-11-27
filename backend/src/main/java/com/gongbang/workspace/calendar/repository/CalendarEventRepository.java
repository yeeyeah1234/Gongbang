package com.gongbang.workspace.calendar.repository;

import com.gongbang.workspace.calendar.domain.CalendarEvent;
import com.gongbang.workspace.calendar.domain.CalendarEventAutoType;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, Long> {

    List<CalendarEvent> findByAutoType(CalendarEventAutoType autoType);

    /**
     * CAL-03 목록: 기간이 겹치는 일정을 모두 (자동 일정 포함). 여러 날 일정이 달 경계에 걸쳐도 나온다. 시작 시각 → ID 순.
     *
     * <p>종일 일정의 end_at은 마지막 날 00:00(종료일 포함)이라 {@code >=}로 비교한다. idx(team_id, start_at)를 탄다.
     */
    @Query(
            "select e from CalendarEvent e"
                    + " where e.teamId = :teamId"
                    + " and e.startAt < :toExclusive"
                    + " and e.endAt >= :fromInclusive"
                    + " order by e.startAt asc, e.id asc")
    List<CalendarEvent> findOverlapping(
            @Param("teamId") Long teamId,
            @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toExclusive") LocalDateTime toExclusive);

    /**
     * CAL-06: 팀의 공모전 마감 자동 일정을 한 문장으로 넣거나 날짜만 고친다. 바뀐 행 수를 돌려준다 (MySQL 기준: 넣음 1, 날짜 바뀜 2, 그대로 0).
     *
     * <ul>
     *   <li>마감일 = 모집글에 contest_id가 있으면 contest.end_date, 없으면 recruit_post.ext_deadline. 그날 00:00으로
     *       넣는다.
     *   <li>recruit_post를 deleted_at으로 거르지 않는다: 모집글이 삭제돼도 팀은 남고 마감일을 읽어야 한다 (PST-004 ④).
     *   <li>모집글이 없거나 마감일이 없으면 아무 행도 넣지 않는다 (예외 없음).
     *   <li>「조회 후 insert/update」가 아니라 UQ(team_id, auto_type)에 기대는 한 문장이라 동시에 불려도 1개만 남는다.
     *   <li>VALUES()는 MySQL 8.0.20부터 폐기 예정이고 행 별칭(AS new)은 INSERT … SELECT에 쓸 수 없어, 파생 테이블의
     *       열(deadline_at)을 ON DUPLICATE KEY UPDATE에서 직접 참조한다 (MySQL 문서의 INSERT … SELECT 방식).
     * </ul>
     *
     * <p>clearAutomatically를 쓰지 않는다: 지원 수락 트랜잭션 안에서 불리므로 영속성 컨텍스트를 비우면 부르는 쪽 엔티티(RecruitPost)의 변경이
     * 저장되지 않는다.
     */
    @Modifying
    @Query(
            value =
                    "INSERT INTO calendar_event"
                            + " (team_id, title, start_at, end_at, all_day, color, created_by,"
                            + " auto_type)"
                            + " SELECT src.team_id, :title, src.deadline_at, src.deadline_at, TRUE,"
                            + " :color, NULL, :autoType"
                            + " FROM ("
                            + " SELECT t.id AS team_id,"
                            + " CAST(CASE WHEN rp.contest_id IS NOT NULL THEN c.end_date"
                            + " ELSE rp.ext_deadline END AS DATETIME) AS deadline_at"
                            + " FROM team t"
                            + " JOIN recruit_post rp ON rp.id = t.post_id"
                            + " LEFT JOIN contest c ON c.id = rp.contest_id"
                            + " WHERE t.id = :teamId"
                            + " ) AS src"
                            + " WHERE src.deadline_at IS NOT NULL"
                            + " ON DUPLICATE KEY UPDATE start_at = deadline_at, end_at = deadline_at",
            nativeQuery = true)
    int upsertContestDeadlineForTeam(
            @Param("teamId") Long teamId,
            @Param("title") String title,
            @Param("color") String color,
            @Param("autoType") String autoType);

    /**
     * CAL-06: 그 공모전(contest_id)을 쓰는 모집글들의 팀 전체에 {@link #upsertContestDeadlineForTeam}과 같은 upsert를
     * 한다. 조건만 {@code rp.contest_id = :contestId}로 다르다. 바뀐 행 수(MySQL 기준)를 돌려준다.
     */
    @Modifying
    @Query(
            value =
                    "INSERT INTO calendar_event"
                            + " (team_id, title, start_at, end_at, all_day, color, created_by,"
                            + " auto_type)"
                            + " SELECT src.team_id, :title, src.deadline_at, src.deadline_at, TRUE,"
                            + " :color, NULL, :autoType"
                            + " FROM ("
                            + " SELECT t.id AS team_id,"
                            + " CAST(CASE WHEN rp.contest_id IS NOT NULL THEN c.end_date"
                            + " ELSE rp.ext_deadline END AS DATETIME) AS deadline_at"
                            + " FROM team t"
                            + " JOIN recruit_post rp ON rp.id = t.post_id"
                            + " LEFT JOIN contest c ON c.id = rp.contest_id"
                            + " WHERE rp.contest_id = :contestId"
                            + " ) AS src"
                            + " WHERE src.deadline_at IS NOT NULL"
                            + " ON DUPLICATE KEY UPDATE start_at = deadline_at, end_at = deadline_at",
            nativeQuery = true)
    int upsertContestDeadlineForContest(
            @Param("contestId") Long contestId,
            @Param("title") String title,
            @Param("color") String color,
            @Param("autoType") String autoType);
}
