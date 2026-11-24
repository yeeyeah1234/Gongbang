package com.gongbang.workspace.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.team.service.TeamService;
import com.gongbang.workspace.document.dto.DocumentListItemResponse;
import java.sql.PreparedStatement;
import java.sql.Statement;
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
 * DOC-01~04를 실제 MySQL에서 확인한다: V22 적용(엔티티 validate), 목록 정렬, 소프트 삭제 후 목록 제외, 50개 제한 동시 생성(AGENTS.md
 * 7).
 *
 * <p>환경 변수 TEST_DATASOURCE_URL에 _test가 있을 때만 돈다 (CalendarMysqlTest와 같은 방식). 테스트가 끝나면 이 테스트가 만든 팀의
 * 문서·채팅·팀원·팀 행과 회원 행만 지운다 (#106 방식).
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
class DocumentMysqlTest {

    // team.post_id는 FK가 없고 UNIQUE라 다른 테스트·이전 실행이 남긴 팀과 겹치지 않는 큰 번호를 쓴다 (#106)
    private static final AtomicLong POST_ID_SEQ =
            new AtomicLong(9_600_000_000L + (System.currentTimeMillis() % 1_000_000L) * 100L);

    @Autowired private DocumentService documentService;
    @Autowired private DocumentQueryService documentQueryService;
    @Autowired private TeamService teamService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private final List<Long> teamIds = new ArrayList<>();
    private final List<Long> memberIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (Long teamId : teamIds) {
            jdbcTemplate.update("DELETE FROM document WHERE team_id = ?", teamId);
            // 팀 채팅방이 team을 가리키므로(fk_chat_room_team) 채팅 행을 먼저 지운다 (TeamMysqlTest와 같음)
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
                    ps.setString(1, "document-" + suffix + "@test.local");
                    ps.setString(2, "dc" + suffix);
                    return ps;
                },
                keyHolder);
        long id = keyHolder.getKey().longValue();
        memberIds.add(id);
        return id;
    }

    private long teamWithLeader(long leaderId) {
        Long id = teamService.createTeam(POST_ID_SEQ.incrementAndGet(), "문서 테스트팀", leaderId);
        teamIds.add(id);
        return id;
    }

    private void setUpdatedAt(long documentId, LocalDateTime at) {
        jdbcTemplate.update("UPDATE document SET updated_at = ? WHERE id = ?", at, documentId);
    }

    private int countRows(long teamId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document WHERE team_id = ?", Integer.class, teamId);
    }

    private void insertDocuments(long teamId, long memberId, int count) {
        for (int i = 0; i < count; i++) {
            jdbcTemplate.update(
                    "INSERT INTO document (team_id, created_by, updated_by) VALUES (?, ?, ?)",
                    teamId,
                    memberId,
                    memberId);
        }
    }

    // ---------- 테스트 ----------

    @Test
    @DisplayName("생성: V22 기본값대로 「제목 없는 문서」, 빈 본문(NULL), 작성자 = 수정한 사람으로 저장된다")
    void 생성이_저장된다() {
        long leaderId = member();
        long teamId = teamWithLeader(leaderId);

        long documentId = documentService.create(leaderId, teamId);

        Map<String, Object> row =
                jdbcTemplate.queryForMap(
                        "SELECT team_id, title, content, created_by, updated_by, created_at,"
                                + " updated_at, deleted_at FROM document WHERE id = ?",
                        documentId);
        assertThat(((Number) row.get("team_id")).longValue()).isEqualTo(teamId);
        assertThat(row.get("title")).isEqualTo("제목 없는 문서");
        assertThat(row.get("content")).isNull();
        assertThat(((Number) row.get("created_by")).longValue()).isEqualTo(leaderId);
        assertThat(((Number) row.get("updated_by")).longValue()).isEqualTo(leaderId);
        assertThat(row.get("created_at")).isNotNull();
        assertThat(row.get("updated_at")).isNotNull();
        assertThat(row.get("deleted_at")).isNull();
    }

    @Test
    @DisplayName("목록: updated_at 최신순, 같은 시각이면 ID 역순. 수정한 사람 닉네임과 canDelete가 채워진다")
    void 목록은_updated_at_최신순() {
        long leaderId = member();
        long memberId = member();
        long teamId = teamWithLeader(leaderId);
        teamService.addMember(teamId, memberId);

        long oldest = documentService.create(leaderId, teamId);
        long sameTimeFirst = documentService.create(memberId, teamId);
        long sameTimeSecond = documentService.create(leaderId, teamId);
        documentService.rename(memberId, sameTimeSecond, "팀원이 고친 제목");
        setUpdatedAt(oldest, LocalDateTime.of(2026, 11, 20, 10, 0));
        setUpdatedAt(sameTimeFirst, LocalDateTime.of(2026, 11, 22, 9, 0));
        setUpdatedAt(sameTimeSecond, LocalDateTime.of(2026, 11, 22, 9, 0));

        List<DocumentListItemResponse> list = documentQueryService.getDocuments(memberId, teamId);

        assertThat(list)
                .extracting(DocumentListItemResponse::id)
                .containsExactly(sameTimeSecond, sameTimeFirst, oldest);
        DocumentListItemResponse renamed = list.get(0);
        assertThat(renamed.title()).isEqualTo("팀원이 고친 제목");
        assertThat(renamed.createdBy()).isEqualTo(leaderId);
        assertThat(renamed.updatedByNickname()).startsWith("dc");
        assertThat(renamed.updatedAt()).isEqualTo(LocalDateTime.of(2026, 11, 22, 9, 0));
        // 팀원은 자기 문서만 지울 수 있다
        assertThat(list)
                .extracting(DocumentListItemResponse::canDelete)
                .containsExactly(false, true, false);
        // 팀장은 모두 지울 수 있다
        assertThat(documentQueryService.getDocuments(leaderId, teamId))
                .extracting(DocumentListItemResponse::canDelete)
                .containsOnly(true);
    }

    @Test
    @DisplayName("삭제: 행은 남고 deleted_at이 기록되며 목록·이름 변경·다시 삭제에서 빠진다(404)")
    void 소프트_삭제된_문서는_목록에서_빠진다() {
        long leaderId = member();
        long memberId = member();
        long teamId = teamWithLeader(leaderId);
        teamService.addMember(teamId, memberId);
        long kept = documentService.create(memberId, teamId);
        long removed = documentService.create(memberId, teamId);

        assertThatThrownBy(() -> documentService.delete(leaderId + memberId + 1_000_000L, removed))
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_TEAM_MEMBER);
        documentService.delete(leaderId, removed); // 작성자가 아니어도 팀장은 지운다

        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT deleted_at FROM document WHERE id = ?",
                                LocalDateTime.class,
                                removed))
                .isNotNull();
        assertThat(documentQueryService.getDocuments(memberId, teamId))
                .extracting(DocumentListItemResponse::id)
                .containsExactly(kept);
        assertThatThrownBy(() -> documentService.rename(memberId, removed, "제목"))
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        assertThatThrownBy(() -> documentService.delete(leaderId, removed))
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("50개 제한: 삭제된 문서는 세지 않는다")
    void 삭제된_문서는_제한에_세지_않는다() {
        long leaderId = member();
        long teamId = teamWithLeader(leaderId);
        insertDocuments(teamId, leaderId, 49);
        long last = documentService.create(leaderId, teamId); // 50번째

        assertThatThrownBy(() -> documentService.create(leaderId, teamId))
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DOCUMENT_LIMIT);

        documentService.delete(leaderId, last);
        documentService.create(leaderId, teamId);
        assertThat(countRows(teamId)).isEqualTo(51); // 삭제된 1개 + 살아 있는 50개
    }

    @Test
    @DisplayName("50개 제한: 48개에서 3명이 동시에 만들어도 2개만 생기고 1명은 409 (AGENTS.md 7)")
    void 동시에_만들어도_50개를_넘지_않는다() throws Exception {
        long leaderId = member();
        long teamId = teamWithLeader(leaderId);
        insertDocuments(teamId, leaderId, 48);

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
                            documentService.create(leaderId, teamId);
                        } catch (BusinessException e) {
                            if (e.getErrorCode() == ErrorCode.DOCUMENT_LIMIT) {
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
        assertThat(countRows(teamId)).isEqualTo(50);
    }
}
