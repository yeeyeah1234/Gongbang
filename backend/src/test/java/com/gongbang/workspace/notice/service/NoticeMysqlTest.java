package com.gongbang.workspace.notice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.team.service.TeamService;
import com.gongbang.workspace.notice.dto.NoticeCreateRequest;
import com.gongbang.workspace.notice.dto.NoticeListItemResponse;
import com.gongbang.workspace.notice.dto.NoticeUpdateRequest;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

/**
 * NTC-01~05 공지를 실제 MySQL에서 확인한다 (V17 생성 열 + UNIQUE, 고정 교체, notice_read CASCADE·upsert, 목록 정렬, 안 읽음
 * 수).
 *
 * <p>환경 변수 TEST_DATASOURCE_URL이 있을 때만 돈다 (CalendarMysqlTest와 같은 방식). 반드시 테스트 전용 스키마(이름에 _test)를
 * 가리키게 한다. MySQL 8 기준이다 (MariaDB는 생성 열 문법이 달라 V17이 다르게 동작할 수 있음). 테스트가 끝나면 이 테스트가 넣은 팀·팀원·회원 행만 지운다
 * (notice·notice_read는 CASCADE).
 *
 * <p>ddl-auto=validate를 켜 Notice·NoticeRead 엔티티가 V17 스키마와 맞는지도 함께 본다.
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
class NoticeMysqlTest {

    // team.post_id는 FK가 없고 UNIQUE라 실행마다 겹치지 않는 큰 번호를 쓴다
    private static final AtomicLong POST_ID_SEQ =
            new AtomicLong(9_300_000_000L + (System.currentTimeMillis() % 1_000_000L) * 100L);

    @Autowired private NoticeService noticeService;
    @Autowired private NoticeQueryService noticeQueryService;
    @Autowired private TeamService teamService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private final List<Long> teamIds = new ArrayList<>();
    private final List<Long> memberIds = new ArrayList<>();

    private long leaderId;
    private long readerId;
    private long teamId;

    @BeforeEach
    void setUp() {
        leaderId = member();
        readerId = member();
        teamId = team(leaderId);
        teamService.addMember(teamId, readerId);
    }

    @AfterEach
    void tearDown() {
        for (Long id : teamIds) {
            // notice 삭제 → notice_read CASCADE. team FK도 CASCADE지만 순서를 분명히 하려고 먼저 지운다.
            jdbcTemplate.update("DELETE FROM notice WHERE team_id = ?", id);
            jdbcTemplate.update("DELETE FROM team_member WHERE team_id = ?", id);
            jdbcTemplate.update("DELETE FROM team WHERE id = ?", id);
        }
        for (Long id : memberIds) {
            jdbcTemplate.update("DELETE FROM member WHERE id = ?", id);
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

    private long member() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        long id =
                insertAndGetId(
                        "INSERT INTO member (email, nickname) VALUES (?, ?)",
                        "notice-" + suffix + "@test.local",
                        "nt" + suffix);
        memberIds.add(id);
        return id;
    }

    private long team(long leader) {
        Long id = teamService.createTeam(POST_ID_SEQ.incrementAndGet(), "공지 테스트팀", leader);
        teamIds.add(id);
        return id;
    }

    private long create(long team, String title, boolean pinned) {
        return noticeService.create(
                leaderOf(team), team, new NoticeCreateRequest(title, "내용", pinned));
    }

    private long leaderOf(long team) {
        return jdbcTemplate.queryForObject(
                "SELECT member_id FROM team_member WHERE team_id = ? AND role = 'LEADER'",
                Long.class,
                team);
    }

    // ---------- 조회 ----------

    private boolean isPinned(long noticeId) {
        return jdbcTemplate.queryForObject(
                "SELECT is_pinned FROM notice WHERE id = ?", Boolean.class, noticeId);
    }

    private int pinnedCount(long team) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notice WHERE team_id = ? AND is_pinned = TRUE",
                Integer.class,
                team);
    }

    private LocalDateTime editedAt(long noticeId) {
        return jdbcTemplate.queryForObject(
                "SELECT edited_at FROM notice WHERE id = ?", LocalDateTime.class, noticeId);
    }

    private int readCount(long noticeId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notice_read WHERE notice_id = ?", Integer.class, noticeId);
    }

    // ---------- 테스트 ----------

    @Test
    @DisplayName("V17이 적용되고 Notice·NoticeRead 엔티티가 스키마와 맞는다 (ddl-auto=validate로 컨텍스트가 뜸)")
    void V17_적용() {
        Boolean success =
                jdbcTemplate.queryForObject(
                        "SELECT success FROM flyway_schema_history WHERE version = '17'",
                        Boolean.class);
        assertThat(success).isTrue();
    }

    @Test
    @DisplayName("팀당 고정 1개를 DB가 막는다: 같은 팀 두 번째 고정은 UNIQUE 위반, 미고정은 여러 개, 다른 팀은 따로 1개")
    void 팀당_고정_1개_UNIQUE() {
        long first = create(teamId, "공지 1", false);
        long second = create(teamId, "공지 2", false);
        create(teamId, "공지 3", false);
        long otherTeam = team(leaderId);
        long otherNotice = create(otherTeam, "다른 팀 공지", false);

        jdbcTemplate.update("UPDATE notice SET is_pinned = TRUE WHERE id = ?", first);
        assertThatThrownBy(
                        () ->
                                jdbcTemplate.update(
                                        "UPDATE notice SET is_pinned = TRUE WHERE id = ?", second))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbcTemplate.update("UPDATE notice SET is_pinned = TRUE WHERE id = ?", otherNotice);

        assertThat(pinnedCount(teamId)).isEqualTo(1);
        assertThat(pinnedCount(otherTeam)).isEqualTo(1);
    }

    @Test
    @DisplayName("고정 교체: 작성(pinned)·고정 API 모두 기존 고정을 풀고 새 공지를 고정한다. edited_at은 그대로")
    void 고정_교체() {
        long a = create(teamId, "공지 A", true);
        long b = create(teamId, "공지 B", true);

        assertThat(isPinned(a)).isFalse();
        assertThat(isPinned(b)).isTrue();
        assertThat(pinnedCount(teamId)).isEqualTo(1);

        noticeService.pin(leaderId, a);

        assertThat(isPinned(a)).isTrue();
        assertThat(isPinned(b)).isFalse();
        assertThat(pinnedCount(teamId)).isEqualTo(1);
        assertThat(editedAt(a)).isNull();
        assertThat(editedAt(b)).isNull();
        assertThat(noticeQueryService.getPinned(readerId, teamId).noticeId()).isEqualTo(a);

        noticeService.unpin(leaderId, a);
        assertThat(pinnedCount(teamId)).isZero();
        assertThat(noticeQueryService.getPinned(readerId, teamId)).isNull();
    }

    @Test
    @DisplayName("두 공지를 동시에 고정해도 고정은 1개이고, 실패한 쪽은 409 NOTICE_PIN_CONFLICT뿐")
    void 동시_고정() throws Exception {
        long a = create(teamId, "공지 A", false);
        long b = create(teamId, "공지 B", false);
        List<Long> targets = List.of(a, b);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();

        for (Long target : targets) {
            executor.submit(
                    () -> {
                        ready.countDown();
                        try {
                            start.await();
                            noticeService.pin(leaderId, target);
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

        assertThat(pinnedCount(teamId)).isEqualTo(1);
        assertThat(errors)
                .allSatisfy(
                        e ->
                                assertThat(e)
                                        .isInstanceOf(BusinessException.class)
                                        .extracting("errorCode")
                                        .isEqualTo(ErrorCode.NOTICE_PIN_CONFLICT));
    }

    @Test
    @DisplayName("수정하면 edited_at이 채워지고 목록에 isEdited로 나온다")
    void 수정_edited_at() {
        long id = create(teamId, "공지", false);

        noticeService.update(leaderId, id, new NoticeUpdateRequest("고친 제목", "고친 내용"));

        assertThat(editedAt(id)).isNotNull();
        assertThat(noticeQueryService.getNotices(readerId, teamId, 0, 10).items().get(0).isEdited())
                .isTrue();
    }

    @Test
    @DisplayName("상세를 두 번·동시에 열어도 notice_read는 1행, 공지를 지우면 notice_read도 지워진다 (CASCADE)")
    void 읽음_기록_중복_없음과_CASCADE() throws Exception {
        long id = create(teamId, "공지", false);

        noticeQueryService.getNotice(readerId, id);
        noticeQueryService.getNotice(readerId, id);
        assertThat(readCount(id)).isEqualTo(1);

        long concurrentId = create(teamId, "동시 공지", false);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < 2; i++) {
            executor.submit(
                    () -> {
                        try {
                            start.await();
                            noticeQueryService.getNotice(readerId, concurrentId);
                        } catch (Throwable t) {
                            errors.add(t);
                        } finally {
                            done.countDown();
                        }
                    });
        }
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();
        assertThat(errors).isEmpty();
        assertThat(readCount(concurrentId)).isEqualTo(1);

        noticeService.delete(leaderId, id);
        assertThat(readCount(id)).isZero();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM notice WHERE id = ?", Integer.class, id))
                .isZero();
    }

    @Test
    @DisplayName("고정 공지를 지우면 배너가 사라지고 다른 공지를 자동 고정하지 않는다")
    void 고정_공지_삭제() {
        create(teamId, "일반 공지", false);
        long pinned = create(teamId, "고정 공지", true);

        noticeService.delete(leaderId, pinned);

        assertThat(pinnedCount(teamId)).isZero();
        assertThat(noticeQueryService.getPinned(readerId, teamId)).isNull();
    }

    @Test
    @DisplayName("목록은 고정 맨 위 → 최신순(같은 초면 나중 ID 먼저), 안 읽음 표시·개수는 작성자 본인을 빼고 센다")
    void 목록_정렬과_안_읽음() {
        long first = create(teamId, "첫째", false);
        long second = create(teamId, "둘째", true);
        long third = create(teamId, "셋째", false);

        List<NoticeListItemResponse> items =
                noticeQueryService.getNotices(readerId, teamId, 0, 10).items();
        assertThat(items)
                .extracting(NoticeListItemResponse::noticeId)
                .containsExactly(second, third, first);
        assertThat(items).allSatisfy(item -> assertThat(item.isUnread()).isTrue());
        assertThat(noticeQueryService.getUnreadCount(readerId, teamId).count()).isEqualTo(3);

        noticeQueryService.getNotice(readerId, third);

        assertThat(noticeQueryService.getUnreadCount(readerId, teamId).count()).isEqualTo(2);
        assertThat(noticeQueryService.getNotices(readerId, teamId, 0, 10).items())
                .extracting(NoticeListItemResponse::isUnread)
                .containsExactly(true, false, true);
        // 작성자(팀장)에게는 자기 공지가 안 읽음이 아니다
        assertThat(noticeQueryService.getUnreadCount(leaderId, teamId).count()).isZero();

        // 2개씩 나누면 고정 공지는 0페이지 맨 위에만 나온다
        assertThat(noticeQueryService.getNotices(readerId, teamId, 1, 2).items())
                .extracting(NoticeListItemResponse::noticeId)
                .containsExactly(first);
    }

    @Test
    @DisplayName("팀원이 아니면 공지 상세 403, 팀원이지만 팀장이 아니면 작성 403")
    void 실제_권한_확인() {
        long outsider = member();
        long id = create(teamId, "공지", false);

        assertThatThrownBy(() -> noticeQueryService.getNotice(outsider, id))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_TEAM_MEMBER);
        assertThatThrownBy(
                        () ->
                                noticeService.create(
                                        readerId,
                                        teamId,
                                        new NoticeCreateRequest("제목", "내용", false)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_TEAM_LEADER);
        assertThat(readCount(id)).isZero();
    }
}
