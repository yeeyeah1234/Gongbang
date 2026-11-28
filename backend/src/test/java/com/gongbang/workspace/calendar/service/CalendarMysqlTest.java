package com.gongbang.workspace.calendar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gongbang.contest.dto.ContestBatchItem;
import com.gongbang.contest.service.ContestBatchService;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.team.service.TeamService;
import com.gongbang.workspace.calendar.dto.CalendarEventRequest;
import com.gongbang.workspace.calendar.dto.CalendarEventResponse;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * CAL-06 공모전 마감 자동 일정을 실제 MySQL에서 확인한다 (네이티브 upsert의 마감일 계산, UQ, 동시 호출, 커밋 후 리스너). CAL-03~05 일반 일정
 * API의 기간 겹침 조회와 실제 저장도 함께 본다.
 *
 * <p>환경 변수 TEST_DATASOURCE_URL이 있을 때만 돈다 (TeamMysqlTest와 같은 방식). 반드시 테스트 전용 스키마(이름에 _test)를 가리키게
 * 한다. 테스트가 끝나면 이 테스트가 넣은 일정·팀·모집글·공모전 행만 지운다.
 *
 * <p>ddl-auto=validate를 켜 CalendarEvent 엔티티가 V16 스키마와 맞는지도 함께 본다 (테스트 설정에서는 메인 application.yml의
 * validate가 적용되지 않음).
 *
 * <pre>
 * TEST_DATASOURCE_URL=jdbc:mysql://127.0.0.1:3307/gongbang_test?serverTimezone=Asia/Seoul
 * </pre>
 */
@SpringBootTest(
        properties = {
            // test application.yml이 끈 DataSource·JPA·Flyway 자동 설정을 이 테스트에서만 다시 켠다.
            "spring.autoconfigure.exclude=",
            "spring.datasource.url=${TEST_DATASOURCE_URL:}",
            "spring.datasource.username=${TEST_DATASOURCE_USERNAME:root}",
            "spring.datasource.password=${TEST_DATASOURCE_PASSWORD:}",
            "spring.jpa.properties.hibernate.show_sql=false",
            "spring.jpa.hibernate.ddl-auto=validate"
        })
@EnabledIfEnvironmentVariable(named = "TEST_DATASOURCE_URL", matches = ".*_test.*")
class CalendarMysqlTest {

    private static final long AUTHOR_ID = 920_001L;
    // recruit_post에 없는 번호 (team.post_id는 FK가 없어 넣을 수 있다)
    private static final long MISSING_POST_ID = 8_920_000_001L;

    @Autowired private CalendarService calendarService;
    @Autowired private CalendarEventService calendarEventService;
    @Autowired private CalendarEventQueryService calendarEventQueryService;
    @Autowired private ContestBatchService contestBatchService;
    @Autowired private TeamService teamService;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    private final List<Long> contestIds = new ArrayList<>();
    private final List<Long> postIds = new ArrayList<>();
    private final List<Long> teamIds = new ArrayList<>();
    // CAL-03~05: calendar_event.created_by는 member FK라 일반 일정 작성자는 실제 회원 행이어야 한다.
    private final List<Long> memberIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (Long teamId : teamIds) {
            jdbcTemplate.update("DELETE FROM calendar_event WHERE team_id = ?", teamId);
            jdbcTemplate.update("DELETE FROM team_member WHERE team_id = ?", teamId);
            jdbcTemplate.update("DELETE FROM team WHERE id = ?", teamId);
        }
        for (Long postId : postIds) {
            jdbcTemplate.update("DELETE FROM recruit_post WHERE id = ?", postId);
        }
        for (Long contestId : contestIds) {
            jdbcTemplate.update("DELETE FROM contest WHERE id = ?", contestId);
        }
        for (Long memberId : memberIds) {
            jdbcTemplate.update("DELETE FROM member WHERE id = ?", memberId);
        }
    }

    // ---------- 데이터 준비 ----------

    private long insertAndGetId(String sql, Object... args) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(
                connection -> {
                    PreparedStatement ps =
                            connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                    for (int i = 0; i < args.length; i++) {
                        ps.setObject(i + 1, args[i]);
                    }
                    return ps;
                },
                keyHolder);
        return keyHolder.getKey().longValue();
    }

    private String newSourceUrl() {
        return "https://test.local/calendar-mysql/" + UUID.randomUUID();
    }

    private long contest(String sourceUrl, LocalDate endDate) {
        long id =
                insertAndGetId(
                        "INSERT INTO contest (source_url, source_site, title, category_id, end_date)"
                                + " VALUES (?, 'WEVITY', '캘린더 테스트 공모전',"
                                + " (SELECT id FROM category WHERE code = 'PLANNING'), ?)",
                        sourceUrl,
                        endDate);
        contestIds.add(id);
        return id;
    }

    private long contest(LocalDate endDate) {
        return contest(newSourceUrl(), endDate);
    }

    private long servicePost(long contestId) {
        long id =
                insertAndGetId(
                        "INSERT INTO recruit_post (author_id, contest_id, title, content,"
                                + " target_count, recruit_start_date, recruit_end_date)"
                                + " VALUES (?, ?, '캘린더 테스트 모집글', '내용', 3, '2026-11-01',"
                                + " '2099-12-01')",
                        AUTHOR_ID,
                        contestId);
        postIds.add(id);
        return id;
    }

    private long externalPost(LocalDate extDeadline) {
        long id =
                insertAndGetId(
                        "INSERT INTO recruit_post (author_id, ext_contest_title, ext_category_id,"
                                + " ext_deadline, title, content, target_count,"
                                + " recruit_start_date, recruit_end_date)"
                                + " VALUES (?, '외부 공모전', (SELECT id FROM category WHERE code ="
                                + " 'PLANNING'), ?, '외부 모집글', '내용', 3, '2026-11-01',"
                                + " '2099-12-01')",
                        AUTHOR_ID,
                        extDeadline);
        postIds.add(id);
        return id;
    }

    private long team(long postId) {
        long id = insertAndGetId("INSERT INTO team (post_id, name) VALUES (?, '캘린더 테스트팀')", postId);
        teamIds.add(id);
        return id;
    }

    private void changeContestEndDate(long contestId, LocalDate endDate) {
        jdbcTemplate.update("UPDATE contest SET end_date = ? WHERE id = ?", endDate, contestId);
    }

    private long member() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        long id =
                insertAndGetId(
                        "INSERT INTO member (email, nickname) VALUES (?, ?)",
                        "calendar-" + suffix + "@test.local",
                        "ce" + suffix);
        memberIds.add(id);
        return id;
    }

    /** 팀장이 있는 팀 (일반 일정 API는 team_member로 권한을 본다). */
    private long teamWithLeader(long postId, long leaderId) {
        Long id = teamService.createTeam(postId, "캘린더 API 테스트팀", leaderId);
        teamIds.add(id);
        return id;
    }

    private long createEvent(
            long memberId,
            long teamId,
            String title,
            LocalDateTime startAt,
            LocalDateTime endAt,
            boolean allDay) {
        return calendarEventService.create(
                memberId,
                teamId,
                new CalendarEventRequest(title, startAt, endAt, allDay, null, null));
    }

    private List<String> titlesBetween(long memberId, long teamId, LocalDate from, LocalDate to) {
        return calendarEventQueryService.getEvents(memberId, teamId, from, to).stream()
                .map(CalendarEventResponse::title)
                .toList();
    }

    private void insertNormalEvent(long teamId, String title) {
        jdbcTemplate.update(
                "INSERT INTO calendar_event (team_id, title, start_at, end_at, created_by)"
                        + " VALUES (?, ?, '2026-12-01 10:00:00', '2026-12-01 11:00:00', NULL)",
                teamId,
                title);
    }

    // ---------- 조회 ----------

    private List<Map<String, Object>> autoDeadlineEvents(long teamId) {
        return jdbcTemplate.queryForList(
                "SELECT id, title, all_day, color, created_by, auto_type FROM calendar_event"
                        + " WHERE team_id = ? AND auto_type = 'CONTEST_DEADLINE'",
                teamId);
    }

    private LocalDateTime autoStartAt(long teamId) {
        return jdbcTemplate.queryForObject(
                "SELECT start_at FROM calendar_event WHERE team_id = ? AND auto_type ="
                        + " 'CONTEST_DEADLINE'",
                LocalDateTime.class,
                teamId);
    }

    private LocalDateTime autoEndAt(long teamId) {
        return jdbcTemplate.queryForObject(
                "SELECT end_at FROM calendar_event WHERE team_id = ? AND auto_type ="
                        + " 'CONTEST_DEADLINE'",
                LocalDateTime.class,
                teamId);
    }

    private int countEvents(long teamId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM calendar_event WHERE team_id = ?", Integer.class, teamId);
    }

    // ---------- 테스트 ----------

    @Test
    @DisplayName("자동 일정이 없으면 「공모전 접수 마감」 하루 종일 일정을 마감일 00:00으로 만든다")
    void 없으면_생성한다() {
        long teamId = team(servicePost(contest(LocalDate.of(2026, 12, 20))));

        calendarService.upsertAutoEvent(teamId);

        List<Map<String, Object>> events = autoDeadlineEvents(teamId);
        assertThat(events).hasSize(1);
        Map<String, Object> event = events.get(0);
        assertThat(event.get("title")).isEqualTo("공모전 접수 마감");
        assertThat(event.get("all_day")).isEqualTo(true);
        assertThat(event.get("color")).isEqualTo("RED");
        assertThat(event.get("created_by")).isNull();
        assertThat(event.get("auto_type")).isEqualTo("CONTEST_DEADLINE");
        assertThat(autoStartAt(teamId)).isEqualTo(LocalDateTime.of(2026, 12, 20, 0, 0));
        assertThat(autoEndAt(teamId)).isEqualTo(LocalDateTime.of(2026, 12, 20, 0, 0));
        assertThat(countEvents(teamId)).isEqualTo(1);
    }

    @Test
    @DisplayName("이미 있으면 같은 행의 날짜만 바꾼다")
    void 있으면_날짜만_갱신한다() {
        long contestId = contest(LocalDate.of(2026, 12, 20));
        long teamId = team(servicePost(contestId));
        calendarService.upsertAutoEvent(teamId);
        Object idBefore = autoDeadlineEvents(teamId).get(0).get("id");

        changeContestEndDate(contestId, LocalDate.of(2027, 1, 5));
        calendarService.upsertAutoEvent(teamId);

        List<Map<String, Object>> events = autoDeadlineEvents(teamId);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).get("id")).isEqualTo(idBefore);
        assertThat(events.get(0).get("title")).isEqualTo("공모전 접수 마감");
        assertThat(autoStartAt(teamId)).isEqualTo(LocalDateTime.of(2027, 1, 5, 0, 0));
        assertThat(autoEndAt(teamId)).isEqualTo(LocalDateTime.of(2027, 1, 5, 0, 0));
        assertThat(countEvents(teamId)).isEqualTo(1);
    }

    @Test
    @DisplayName("두 번 불러도 자동 일정은 팀당 1개")
    void 두_번_불러도_종류별_1개() {
        long teamId = team(servicePost(contest(LocalDate.of(2026, 12, 20))));

        calendarService.upsertAutoEvent(teamId);
        calendarService.upsertAutoEvent(teamId);

        assertThat(countEvents(teamId)).isEqualTo(1);
    }

    @Test
    @DisplayName("외부 공모전 모집글은 ext_deadline을 마감일로 쓴다")
    void 외부_공모전은_ext_deadline을_쓴다() {
        long teamId = team(externalPost(LocalDate.of(2026, 12, 25)));

        calendarService.upsertAutoEvent(teamId);

        assertThat(autoDeadlineEvents(teamId)).hasSize(1);
        assertThat(autoStartAt(teamId)).isEqualTo(LocalDateTime.of(2026, 12, 25, 0, 0));
    }

    @Test
    @DisplayName("삭제된 모집글(deleted_at 있음)의 팀도 자동 일정을 만든다 (PST-004 ④)")
    void 삭제된_모집글의_팀도_생성한다() {
        long postId = servicePost(contest(LocalDate.of(2026, 12, 20)));
        jdbcTemplate.update("UPDATE recruit_post SET deleted_at = NOW() WHERE id = ?", postId);
        long teamId = team(postId);

        calendarService.upsertAutoEvent(teamId);

        assertThat(autoDeadlineEvents(teamId)).hasSize(1);
        assertThat(autoStartAt(teamId)).isEqualTo(LocalDateTime.of(2026, 12, 20, 0, 0));
    }

    @Test
    @DisplayName("모집글이 없는 팀이나 없는 팀 ID면 아무것도 만들지 않고 예외도 없다")
    void 모집글이_없으면_아무것도_하지_않는다() {
        long teamId = team(MISSING_POST_ID);

        assertThatCode(() -> calendarService.upsertAutoEvent(teamId)).doesNotThrowAnyException();
        assertThatCode(() -> calendarService.upsertAutoEvent(Long.MAX_VALUE))
                .doesNotThrowAnyException();

        assertThat(countEvents(teamId)).isZero();
        // 마감일이 없는 모집글은 recruit_post CHECK 제약 때문에 만들 수 없다 (SQL의 IS NOT NULL은 방어용)
    }

    @Test
    @DisplayName("upsertAutoEventsByContest: 같은 공모전의 팀은 모두 갱신하고 다른 공모전 팀은 그대로 둔다")
    void 공모전_기준으로_팀_전체를_갱신한다() {
        long contestA = contest(LocalDate.of(2026, 12, 20));
        long contestB = contest(LocalDate.of(2026, 12, 21));
        long teamA1 = team(servicePost(contestA));
        long teamA2 = team(servicePost(contestA)); // 아직 자동 일정 없음
        long teamB = team(servicePost(contestB));
        calendarService.upsertAutoEvent(teamA1);
        calendarService.upsertAutoEvent(teamB);

        changeContestEndDate(contestA, LocalDate.of(2027, 2, 1));
        changeContestEndDate(contestB, LocalDate.of(2027, 3, 1));
        calendarService.upsertAutoEventsByContest(contestA);

        assertThat(autoStartAt(teamA1)).isEqualTo(LocalDateTime.of(2027, 2, 1, 0, 0));
        assertThat(autoStartAt(teamA2)).isEqualTo(LocalDateTime.of(2027, 2, 1, 0, 0));
        assertThat(autoStartAt(teamB)).isEqualTo(LocalDateTime.of(2026, 12, 21, 0, 0));
        assertThat(countEvents(teamA1)).isEqualTo(1);
        assertThat(countEvents(teamA2)).isEqualTo(1);
    }

    @Test
    @DisplayName("두 스레드가 같은 팀을 동시에 불러도 오류 없이 1개")
    void 동시에_불러도_종류별_1개() throws Exception {
        long teamId = team(servicePost(contest(LocalDate.of(2026, 12, 20))));
        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < threads; i++) {
            executor.submit(
                    () -> {
                        ready.countDown();
                        try {
                            start.await();
                            calendarService.upsertAutoEvent(teamId);
                        } catch (Throwable t) {
                            errors.add(t);
                        } finally {
                            done.countDown();
                        }
                    });
        }
        ready.await();
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        assertThat(errors).isEmpty();
        assertThat(countEvents(teamId)).isEqualTo(1);
    }

    @Test
    @DisplayName("일반 일정(auto_type NULL)은 같은 팀에 여러 개 넣을 수 있고 자동 일정과 함께 있다")
    void 일반_일정은_여러_개_가능하다() {
        long teamId = team(servicePost(contest(LocalDate.of(2026, 12, 20))));

        insertNormalEvent(teamId, "회의 1");
        insertNormalEvent(teamId, "회의 2");
        calendarService.upsertAutoEvent(teamId);

        assertThat(countEvents(teamId)).isEqualTo(3);
        assertThat(autoDeadlineEvents(teamId)).hasSize(1);
    }

    @Test
    @DisplayName("지원 수락처럼 READ COMMITTED 트랜잭션에서 팀을 만든 직후 불러도 생성된다 (TeamJoinService 경로)")
    void 같은_트랜잭션에서_만든_팀에도_생성된다() {
        long postId = servicePost(contest(LocalDate.of(2026, 12, 20)));
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);

        Long teamId =
                tx.execute(
                        status -> {
                            Long id = teamService.createTeam(postId, "수락 경로팀", AUTHOR_ID);
                            calendarService.upsertAutoEvent(id);
                            return id;
                        });
        teamIds.add(teamId);

        assertThat(autoDeadlineEvents(teamId)).hasSize(1);
        assertThat(autoStartAt(teamId)).isEqualTo(LocalDateTime.of(2026, 12, 20, 0, 0));
    }

    @Test
    @DisplayName("크롤러 배치가 마감일을 바꾸면 커밋 후 리스너가 자동 일정 날짜를 바꾼다 (CRW-01 ⑦)")
    void 배치_마감일_변경이_자동_일정에_반영된다() {
        String sourceUrl = newSourceUrl();
        long contestId = contest(sourceUrl, LocalDate.of(2026, 12, 20));
        long teamId = team(servicePost(contestId));
        calendarService.upsertAutoEvent(teamId);

        contestBatchService.upsert(
                List.of(
                        new ContestBatchItem(
                                sourceUrl,
                                "WEVITY",
                                "캘린더 테스트 공모전",
                                null,
                                "PLANNING",
                                null,
                                null,
                                LocalDate.of(2027, 4, 1),
                                null,
                                null)));

        assertThat(countEvents(teamId)).isEqualTo(1);
        assertThat(autoStartAt(teamId)).isEqualTo(LocalDateTime.of(2027, 4, 1, 0, 0));
    }

    // ---------- CAL-03~05 일반 일정 API ----------

    @Test
    @DisplayName("목록: 기간이 겹치는 일정을 모두 준다 (달 경계에 걸친 여러 날 일정은 두 달 모두, 자동 일정 포함, 시작 시각 순)")
    void 기간이_겹치는_일정을_모두_준다() {
        long leaderId = member();
        long teamId = teamWithLeader(servicePost(contest(LocalDate.of(2026, 12, 20))), leaderId);
        calendarService.upsertAutoEvent(teamId); // 12/20 종일 「공모전 접수 마감」

        createEvent(
                leaderId,
                teamId,
                "달 경계 종일",
                LocalDateTime.of(2026, 11, 28, 0, 0),
                LocalDateTime.of(2026, 12, 3, 0, 0),
                true);
        createEvent(
                leaderId,
                teamId,
                "10월 말 밤샘",
                LocalDateTime.of(2026, 10, 31, 22, 0),
                LocalDateTime.of(2026, 11, 1, 2, 0),
                false);
        createEvent(
                leaderId,
                teamId,
                "11월 마지막 날",
                LocalDateTime.of(2026, 11, 30, 0, 0),
                LocalDateTime.of(2026, 11, 30, 0, 0),
                true);
        createEvent(
                leaderId,
                teamId,
                "12월 회의",
                LocalDateTime.of(2026, 12, 1, 10, 0),
                LocalDateTime.of(2026, 12, 1, 11, 0),
                false);
        createEvent(
                leaderId,
                teamId,
                "10월 하루",
                LocalDateTime.of(2026, 10, 30, 0, 0),
                LocalDateTime.of(2026, 10, 30, 0, 0),
                true);

        assertThat(
                        titlesBetween(
                                leaderId,
                                teamId,
                                LocalDate.of(2026, 11, 1),
                                LocalDate.of(2026, 11, 30)))
                .containsExactly("10월 말 밤샘", "달 경계 종일", "11월 마지막 날");
        assertThat(
                        titlesBetween(
                                leaderId,
                                teamId,
                                LocalDate.of(2026, 12, 1),
                                LocalDate.of(2026, 12, 31)))
                .containsExactly("달 경계 종일", "12월 회의", "공모전 접수 마감");
    }

    @Test
    @DisplayName("생성 → 다른 팀원 수정 403 → 팀장 수정 → 팀장 삭제가 실제로 저장된다, 자동 일정은 팀장도 못 지운다")
    void 생성_수정_삭제가_저장된다() {
        long leaderId = member();
        long authorId = member();
        long otherId = member();
        long teamId = teamWithLeader(servicePost(contest(LocalDate.of(2026, 12, 20))), leaderId);
        teamService.addMember(teamId, authorId);
        teamService.addMember(teamId, otherId);
        calendarService.upsertAutoEvent(teamId);

        long eventId =
                calendarEventService.create(
                        authorId,
                        teamId,
                        new CalendarEventRequest(
                                "  회의  ",
                                LocalDateTime.of(2026, 11, 25, 9, 30),
                                LocalDateTime.of(2026, 11, 26, 18, 0),
                                true,
                                "GREEN",
                                "   "));

        Map<String, Object> row =
                jdbcTemplate.queryForMap(
                        "SELECT title, start_at, end_at, all_day, color, memo, created_by,"
                                + " auto_type FROM calendar_event WHERE id = ?",
                        eventId);
        assertThat(row.get("title")).isEqualTo("회의");
        assertThat(row.get("start_at")).isEqualTo(LocalDateTime.of(2026, 11, 25, 0, 0));
        assertThat(row.get("end_at")).isEqualTo(LocalDateTime.of(2026, 11, 26, 0, 0));
        assertThat(row.get("all_day")).isEqualTo(true);
        assertThat(row.get("color")).isEqualTo("GREEN");
        assertThat(row.get("memo")).isNull();
        assertThat(((Number) row.get("created_by")).longValue()).isEqualTo(authorId);
        assertThat(row.get("auto_type")).isNull();

        CalendarEventResponse detail = calendarEventQueryService.getEvent(otherId, eventId);
        assertThat(detail.canEdit()).isFalse();
        assertThat(detail.createdAt()).isNotNull();
        CalendarEventRequest change =
                new CalendarEventRequest(
                        "팀장이 고침",
                        LocalDateTime.of(2026, 11, 27, 14, 0),
                        LocalDateTime.of(2026, 11, 27, 15, 0),
                        false,
                        null,
                        "메모");
        assertThatThrownBy(() -> calendarEventService.update(otherId, eventId, change))
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_EVENT_AUTHOR);

        calendarEventService.update(leaderId, eventId, change);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT title FROM calendar_event WHERE id = ?",
                                String.class,
                                eventId))
                .isEqualTo("팀장이 고침");

        Long autoId =
                jdbcTemplate.queryForObject(
                        "SELECT id FROM calendar_event WHERE team_id = ? AND auto_type = 'CONTEST_DEADLINE'",
                        Long.class,
                        teamId);
        assertThatThrownBy(() -> calendarEventService.delete(leaderId, autoId))
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTO_EVENT_NOT_EDITABLE);

        calendarEventService.delete(leaderId, eventId);
        assertThat(countEvents(teamId)).isEqualTo(1); // 자동 일정만 남음
    }
}
