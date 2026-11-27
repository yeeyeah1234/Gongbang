package com.gongbang.workspace.whiteboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.team.service.TeamService;
import com.gongbang.workspace.whiteboard.dto.WhiteboardAccessResponse;
import com.gongbang.workspace.whiteboard.dto.WhiteboardListItemResponse;
import com.gongbang.workspace.whiteboard.dto.WhiteboardListItemResponse.LastEditor;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

/**
 * WB-01·WB-10을 실제 MySQL에서 확인한다: V24 적용(엔티티 validate), 목록 정렬·lastEditor, 소프트 삭제, 30개 제한 동시
 * 생성(AGENTS.md 7), LONGBLOB 바이트 왕복과 last_editor_id·팀 최근 활동 시각 갱신, 입장 확인 colorIndex.
 *
 * <p>환경 변수 TEST_DATASOURCE_URL에 _test가 있을 때만 돈다 (DocumentMysqlTest와 같은 설정). 이 테스트가 만든 행만 지운다.
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
class WhiteboardMysqlTest {

    // 문서 MySQL 테스트(9_6xx·9_7xx)와 겹치지 않는 범위 (#106)
    private static final AtomicLong POST_ID_SEQ =
            new AtomicLong(9_800_000_000L + (System.currentTimeMillis() % 1_000_000L) * 100L);

    @Autowired private WhiteboardService whiteboardService;
    @Autowired private WhiteboardQueryService whiteboardQueryService;
    @Autowired private WhiteboardSyncService whiteboardSyncService;
    @Autowired private TeamService teamService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private final List<Long> teamIds = new ArrayList<>();
    private final List<Long> memberIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (Long teamId : teamIds) {
            jdbcTemplate.update("DELETE FROM whiteboard WHERE team_id = ?", teamId);
            // 팀 채팅방이 team을 가리키므로(fk_chat_room_team) 채팅 행을 먼저 지운다 (DocumentMysqlTest와 같음)
            String teamRooms = "SELECT id FROM chat_room WHERE team_id = ?";
            jdbcTemplate.update(
                    "DELETE FROM chat_message WHERE chat_room_id IN (" + teamRooms + ")", teamId);
            jdbcTemplate.update(
                    "DELETE FROM chat_room_member WHERE chat_room_id IN (" + teamRooms + ")",
                    teamId);
            jdbcTemplate.update("DELETE FROM chat_room WHERE team_id = ?", teamId);
            jdbcTemplate.update("DELETE FROM team_member WHERE team_id = ?", teamId);
            jdbcTemplate.update("DELETE FROM team WHERE id = ?", teamId);
        }
        for (Long memberId : memberIds) {
            jdbcTemplate.update("DELETE FROM member WHERE id = ?", memberId);
        }
    }

    // ---------- 데이터 준비 ----------

    private long member() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(
                connection -> {
                    PreparedStatement ps =
                            connection.prepareStatement(
                                    "INSERT INTO member (email, nickname) VALUES (?, ?)",
                                    Statement.RETURN_GENERATED_KEYS);
                    ps.setString(1, "whiteboard-" + suffix + "@test.local");
                    ps.setString(2, "wb" + suffix);
                    return ps;
                },
                keyHolder);
        long id = keyHolder.getKey().longValue();
        memberIds.add(id);
        return id;
    }

    private long teamWithLeader(long leaderId) {
        Long id = teamService.createTeam(POST_ID_SEQ.incrementAndGet(), "보드 테스트팀", leaderId);
        teamIds.add(id);
        return id;
    }

    private void setUpdatedAt(long whiteboardId, LocalDateTime at) {
        jdbcTemplate.update("UPDATE whiteboard SET updated_at = ? WHERE id = ?", at, whiteboardId);
    }

    private int countRows(long teamId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM whiteboard WHERE team_id = ?", Integer.class, teamId);
    }

    private void insertWhiteboards(long teamId, long memberId, int count) {
        for (int i = 0; i < count; i++) {
            jdbcTemplate.update(
                    "INSERT INTO whiteboard (team_id, creator_id) VALUES (?, ?)", teamId, memberId);
        }
    }

    // ---------- 만들기·목록·삭제 (WB-01) ----------

    @Test
    @DisplayName("만들기: V24 기본값대로 「제목 없는 보드」, 빈 본문(NULL), 마지막 수정자 NULL로 저장된다")
    void 만들기가_저장된다() {
        long leaderId = member();
        long teamId = teamWithLeader(leaderId);

        long whiteboardId = whiteboardService.create(leaderId, teamId).whiteboardId();

        Map<String, Object> row =
                jdbcTemplate.queryForMap(
                        "SELECT team_id, title, content, creator_id, last_editor_id, created_at,"
                                + " updated_at, deleted_at FROM whiteboard WHERE id = ?",
                        whiteboardId);
        assertThat(((Number) row.get("team_id")).longValue()).isEqualTo(teamId);
        assertThat(row.get("title")).isEqualTo("제목 없는 보드");
        assertThat(row.get("content")).isNull();
        assertThat(((Number) row.get("creator_id")).longValue()).isEqualTo(leaderId);
        assertThat(row.get("last_editor_id")).isNull();
        assertThat(row.get("created_at")).isNotNull();
        assertThat(row.get("updated_at")).isNotNull();
        assertThat(row.get("deleted_at")).isNull();
    }

    @Test
    @DisplayName("만들기: 기본 제목 번호가 증가하고, 삭제·이름 변경된 보드와 이상한 형태는 무시한다")
    void 기본_제목_번호가_증가한다() {
        long leaderId = member();
        long teamId = teamWithLeader(leaderId);

        var first = whiteboardService.create(leaderId, teamId);
        var second = whiteboardService.create(leaderId, teamId);
        var third = whiteboardService.create(leaderId, teamId);
        assertThat(first.title()).isEqualTo("제목 없는 보드");
        assertThat(second.title()).isEqualTo("제목 없는 보드 (2)");
        assertThat(third.title()).isEqualTo("제목 없는 보드 (3)");

        // (3)을 삭제하고 (2)를 사용자 제목·복사본으로 바꾸면 살아 있는 기본 제목 중 최댓값은 1 → (2)
        whiteboardService.delete(leaderId, third.whiteboardId());
        whiteboardService.rename(leaderId, second.whiteboardId(), "제목 없는 보드 (복사본)");
        var afterDelete = whiteboardService.create(leaderId, teamId);
        assertThat(afterDelete.title()).isEqualTo("제목 없는 보드 (2)");

        // (5)를 만들어 두면 다음 번호는 (6)
        whiteboardService.rename(leaderId, afterDelete.whiteboardId(), "제목 없는 보드 (5)");
        var sixth = whiteboardService.create(leaderId, teamId);
        assertThat(sixth.title()).isEqualTo("제목 없는 보드 (6)");
    }

    @Test
    @DisplayName("목록: updated_at 최신순, 같은 시각이면 ID 역순. 제목 변경은 lastEditor를 바꾸지 않는다")
    void 목록은_updated_at_최신순() {
        long leaderId = member();
        long memberId = member();
        long teamId = teamWithLeader(leaderId);
        teamService.addMember(teamId, memberId);

        long oldest = whiteboardService.create(leaderId, teamId).whiteboardId();
        long sameTimeFirst = whiteboardService.create(memberId, teamId).whiteboardId();
        long sameTimeSecond = whiteboardService.create(leaderId, teamId).whiteboardId();
        whiteboardService.rename(memberId, sameTimeSecond, "팀원이 고친 제목");
        setUpdatedAt(oldest, LocalDateTime.of(2026, 11, 20, 10, 0));
        setUpdatedAt(sameTimeFirst, LocalDateTime.of(2026, 11, 22, 9, 0));
        setUpdatedAt(sameTimeSecond, LocalDateTime.of(2026, 11, 22, 9, 0));

        List<WhiteboardListItemResponse> list =
                whiteboardQueryService.getWhiteboards(memberId, teamId);

        assertThat(list)
                .extracting(WhiteboardListItemResponse::whiteboardId)
                .containsExactly(sameTimeSecond, sameTimeFirst, oldest);
        WhiteboardListItemResponse renamed = list.get(0);
        assertThat(renamed.title()).isEqualTo("팀원이 고친 제목");
        assertThat(renamed.creatorId()).isEqualTo(leaderId);
        assertThat(renamed.lastEditor()).isNull();
        assertThat(renamed.updatedAt()).isEqualTo(LocalDateTime.of(2026, 11, 22, 9, 0));
    }

    @Test
    @DisplayName("삭제: 행은 남고 deleted_at이 기록되며 목록·제목 변경·다시 삭제에서 빠진다(404)")
    void 소프트_삭제된_보드는_목록에서_빠진다() {
        long leaderId = member();
        long memberId = member();
        long teamId = teamWithLeader(leaderId);
        teamService.addMember(teamId, memberId);
        long kept = whiteboardService.create(memberId, teamId).whiteboardId();
        long removed = whiteboardService.create(memberId, teamId).whiteboardId();

        assertThatThrownBy(() -> whiteboardService.delete(leaderId, kept + 1_000_000L))
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        whiteboardService.delete(leaderId, removed); // 만든 사람이 아니어도 팀장은 지운다

        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT deleted_at FROM whiteboard WHERE id = ?",
                                LocalDateTime.class,
                                removed))
                .isNotNull();
        assertThat(whiteboardQueryService.getWhiteboards(memberId, teamId))
                .extracting(WhiteboardListItemResponse::whiteboardId)
                .containsExactly(kept);
        assertThatThrownBy(() -> whiteboardService.rename(memberId, removed, "제목"))
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        assertThatThrownBy(() -> whiteboardService.delete(leaderId, removed))
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("30개 제한: 삭제된 보드는 세지 않는다")
    void 삭제된_보드는_제한에_세지_않는다() {
        long leaderId = member();
        long teamId = teamWithLeader(leaderId);
        insertWhiteboards(teamId, leaderId, 29);
        long last = whiteboardService.create(leaderId, teamId).whiteboardId(); // 30번째

        assertThatThrownBy(() -> whiteboardService.create(leaderId, teamId))
                .extracting("errorCode")
                .isEqualTo(ErrorCode.WHITEBOARD_LIMIT);

        whiteboardService.delete(leaderId, last);
        whiteboardService.create(leaderId, teamId);
        assertThat(countRows(teamId)).isEqualTo(31); // 삭제된 1개 + 살아 있는 30개
    }

    @Test
    @DisplayName("30개 제한: 28개에서 3명이 동시에 만들어도 2개만 생기고 1명은 409 (AGENTS.md 7)")
    void 동시에_만들어도_30개를_넘지_않는다() throws Exception {
        long leaderId = member();
        long teamId = teamWithLeader(leaderId);
        insertWhiteboards(teamId, leaderId, 28);

        int threads = 3;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger limited = new AtomicInteger();
        ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < threads; i++) {
            executor.submit(
                    () -> {
                        ready.countDown();
                        try {
                            start.await();
                            whiteboardService.create(leaderId, teamId);
                        } catch (BusinessException e) {
                            if (e.getErrorCode() == ErrorCode.WHITEBOARD_LIMIT) {
                                limited.incrementAndGet();
                            } else {
                                errors.add(e);
                            }
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
        assertThat(limited.get()).isEqualTo(1);
        assertThat(countRows(teamId)).isEqualTo(30);
    }

    // ---------- 동기화 서버 (WB-10) ----------

    @Test
    @DisplayName("본문: 모든 바이트 값이 그대로 왕복하고, 저장하면 last_editor_id·updated_at·팀 최근 활동 시각이 바뀐다")
    void 본문_왕복과_마지막_수정자() {
        long leaderId = member();
        long memberId = member();
        long teamId = teamWithLeader(leaderId);
        teamService.addMember(teamId, memberId);
        long whiteboardId = whiteboardService.create(leaderId, teamId).whiteboardId();
        setUpdatedAt(whiteboardId, LocalDateTime.of(2026, 1, 1, 0, 0));
        jdbcTemplate.update(
                "UPDATE team SET last_activity_at = ? WHERE id = ?",
                LocalDateTime.of(2026, 1, 1, 0, 0),
                teamId);

        assertThat(whiteboardSyncService.loadContent(whiteboardId)).isEmpty();

        byte[] bytes = new byte[300_000];
        new Random(42).nextBytes(bytes);
        whiteboardSyncService.saveContent(whiteboardId, memberId, bytes);

        assertThat(whiteboardSyncService.loadContent(whiteboardId)).contains(bytes);
        Map<String, Object> row =
                jdbcTemplate.queryForMap(
                        "SELECT last_editor_id, updated_at FROM whiteboard WHERE id = ?",
                        whiteboardId);
        assertThat(((Number) row.get("last_editor_id")).longValue()).isEqualTo(memberId);
        assertThat((LocalDateTime) row.get("updated_at"))
                .isAfter(LocalDateTime.of(2026, 1, 1, 0, 0));
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT last_activity_at FROM team WHERE id = ?",
                                LocalDateTime.class,
                                teamId))
                .isAfter(LocalDateTime.of(2026, 1, 1, 0, 0));
        assertThat(whiteboardQueryService.getWhiteboards(leaderId, teamId))
                .singleElement()
                .extracting(WhiteboardListItemResponse::lastEditor)
                .extracting(LastEditor::memberId)
                .isEqualTo(memberId);
    }

    @Test
    @DisplayName("본문: 삭제된 보드는 불러오기·저장 모두 404, 저장해도 행이 바뀌지 않는다")
    void 삭제된_보드_본문은_404() {
        long leaderId = member();
        long teamId = teamWithLeader(leaderId);
        long whiteboardId = whiteboardService.create(leaderId, teamId).whiteboardId();
        whiteboardService.delete(leaderId, whiteboardId);

        assertThatThrownBy(() -> whiteboardSyncService.loadContent(whiteboardId))
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        assertThatThrownBy(
                        () ->
                                whiteboardSyncService.saveContent(
                                        whiteboardId, leaderId, new byte[] {1, 2}))
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT content FROM whiteboard WHERE id = ?",
                                byte[].class,
                                whiteboardId))
                .isNull();
    }

    @Test
    @DisplayName("입장 확인: 팀원은 자기 colorIndex를 받고, 팀원이 아니면 403 NOT_TEAM_MEMBER")
    void 입장_확인() {
        long leaderId = member();
        long memberId = member();
        long outsiderId = member();
        long teamId = teamWithLeader(leaderId);
        teamService.addMember(teamId, memberId);
        long whiteboardId = whiteboardService.create(leaderId, teamId).whiteboardId();
        int expectedColor =
                jdbcTemplate.queryForObject(
                        "SELECT color_index FROM team_member WHERE team_id = ? AND member_id = ?",
                        Integer.class,
                        teamId,
                        memberId);

        WhiteboardAccessResponse response =
                whiteboardSyncService.checkAccess(memberId, whiteboardId);

        assertThat(response.whiteboardId()).isEqualTo(whiteboardId);
        assertThat(response.teamId()).isEqualTo(teamId);
        assertThat(response.memberId()).isEqualTo(memberId);
        assertThat(response.nickname()).startsWith("wb");
        assertThat(response.colorIndex()).isEqualTo(expectedColor);
        assertThatThrownBy(() -> whiteboardSyncService.checkAccess(outsiderId, whiteboardId))
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_TEAM_MEMBER);
    }
}
