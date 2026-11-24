package com.gongbang.workspace.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gongbang.global.error.ErrorCode;
import com.gongbang.team.service.TeamService;
import com.gongbang.workspace.document.dto.DocumentAccessResponse;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
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
 * DOC-08·DOC-10 동기화 서버용 본문 저장·불러오기를 실제 MySQL에서 확인한다: LONGBLOB 바이트 왕복, updated_by·updated_at·팀 최근 활동
 * 시각 갱신, 삭제된 문서 404, 권한 확인.
 *
 * <p>환경 변수 TEST_DATASOURCE_URL에 _test가 있을 때만 돈다 ({@link DocumentMysqlTest}와 같은 설정). 이 테스트가 만든 행만
 * 지운다.
 */
@SpringBootTest(
        properties = {
            "spring.autoconfigure.exclude=",
            "spring.datasource.url=${TEST_DATASOURCE_URL:}",
            "spring.datasource.username=${TEST_DATASOURCE_USERNAME:root}",
            "spring.datasource.password=${TEST_DATASOURCE_PASSWORD:}",
            "spring.jpa.properties.hibernate.show_sql=false",
            "spring.jpa.hibernate.ddl-auto=validate"
        })
@EnabledIfEnvironmentVariable(named = "TEST_DATASOURCE_URL", matches = ".*_test.*")
class DocumentContentMysqlTest {

    // DocumentMysqlTest와 겹치지 않는 범위 (#106)
    private static final AtomicLong POST_ID_SEQ =
            new AtomicLong(9_700_000_000L + (System.currentTimeMillis() % 1_000_000L) * 100L);

    @Autowired private DocumentService documentService;
    @Autowired private DocumentSyncService documentSyncService;
    @Autowired private TeamService teamService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private final List<Long> teamIds = new ArrayList<>();
    private final List<Long> memberIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (Long teamId : teamIds) {
            jdbcTemplate.update("DELETE FROM document WHERE team_id = ?", teamId);
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
                    ps.setString(1, "doc-content-" + suffix + "@test.local");
                    ps.setString(2, "dk" + suffix);
                    return ps;
                },
                keyHolder);
        long id = keyHolder.getKey().longValue();
        memberIds.add(id);
        return id;
    }

    private long teamWithLeader(long leaderId) {
        Long id = teamService.createTeam(POST_ID_SEQ.incrementAndGet(), "문서 본문 테스트팀", leaderId);
        teamIds.add(id);
        return id;
    }

    /** 0x00·0xFF를 포함한 1MB 남짓의 임의 바이트 (고정 seed). */
    private static byte[] yjsLikeBytes() {
        byte[] bytes = new byte[1024 * 1024 + 7];
        new Random(42).nextBytes(bytes);
        bytes[0] = 0x00;
        bytes[1] = (byte) 0xFF;
        bytes[bytes.length - 1] = 0x00;
        return bytes;
    }

    // ---------- 테스트 ----------

    @Test
    @DisplayName("본문: 새 문서는 비어 있고, 저장한 LONGBLOB 바이트가 그대로 돌아온다")
    void LONGBLOB_왕복() {
        long leaderId = member();
        long teamId = teamWithLeader(leaderId);
        long documentId = documentService.create(leaderId, teamId);
        assertThat(documentSyncService.loadContent(documentId)).isEmpty();

        byte[] bytes = yjsLikeBytes();
        documentSyncService.saveContent(documentId, leaderId, bytes);

        assertThat(documentSyncService.loadContent(documentId))
                .hasValueSatisfying(loaded -> assertThat(loaded).isEqualTo(bytes));
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT LENGTH(content) FROM document WHERE id = ?",
                                Long.class,
                                documentId))
                .isEqualTo(bytes.length);
    }

    @Test
    @DisplayName("본문: 빈 바이트를 저장하면 불러올 때 빈 값(204)")
    void 빈_본문은_빈_값() {
        long leaderId = member();
        long teamId = teamWithLeader(leaderId);
        long documentId = documentService.create(leaderId, teamId);
        documentSyncService.saveContent(documentId, leaderId, new byte[] {1, 2, 3});

        documentSyncService.saveContent(documentId, leaderId, new byte[0]);

        assertThat(documentSyncService.loadContent(documentId)).isEmpty();
    }

    @Test
    @DisplayName("저장: updated_by를 편집자로, updated_at과 팀 최근 활동 시각을 갱신한다 (같은 바이트여도)")
    void 저장하면_수정자와_시각이_바뀐다() {
        long leaderId = member();
        long editorId = member();
        long teamId = teamWithLeader(leaderId);
        teamService.addMember(teamId, editorId);
        long documentId = documentService.create(leaderId, teamId);
        byte[] bytes = {9, 9, 9};
        documentSyncService.saveContent(documentId, leaderId, bytes);
        LocalDateTime old = LocalDateTime.of(2026, 1, 1, 0, 0);
        jdbcTemplate.update("UPDATE document SET updated_at = ? WHERE id = ?", old, documentId);
        jdbcTemplate.update("UPDATE team SET last_activity_at = ? WHERE id = ?", old, teamId);

        documentSyncService.saveContent(documentId, editorId, bytes);

        Map<String, Object> row =
                jdbcTemplate.queryForMap(
                        "SELECT d.updated_by, d.updated_at, t.last_activity_at FROM document d"
                                + " JOIN team t ON t.id = d.team_id WHERE d.id = ?",
                        documentId);
        assertThat(((Number) row.get("updated_by")).longValue()).isEqualTo(editorId);
        assertThat((LocalDateTime) row.get("updated_at")).isAfter(old);
        assertThat((LocalDateTime) row.get("last_activity_at")).isAfter(old);
    }

    @Test
    @DisplayName("삭제된 문서: 불러오기·저장·권한 확인 모두 404, 본문은 바뀌지 않는다")
    void 삭제된_문서는_404() {
        long leaderId = member();
        long teamId = teamWithLeader(leaderId);
        long documentId = documentService.create(leaderId, teamId);
        documentSyncService.saveContent(documentId, leaderId, new byte[] {1, 2, 3});
        documentService.delete(leaderId, documentId);

        assertThatThrownBy(() -> documentSyncService.loadContent(documentId))
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        assertThatThrownBy(
                        () -> documentSyncService.saveContent(documentId, leaderId, new byte[] {7}))
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        assertThatThrownBy(() -> documentSyncService.checkAccess(leaderId, documentId))
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT content FROM document WHERE id = ?",
                                byte[].class,
                                documentId))
                .isEqualTo(new byte[] {1, 2, 3});
    }

    @Test
    @DisplayName("권한 확인: 팀장·팀원은 정보를 받고, 다른 팀 회원은 403")
    void 권한_확인() {
        long leaderId = member();
        long memberId = member();
        long outsiderId = member();
        long teamId = teamWithLeader(leaderId);
        teamService.addMember(teamId, memberId);
        long documentId = documentService.create(memberId, teamId);

        DocumentAccessResponse leader = documentSyncService.checkAccess(leaderId, documentId);
        DocumentAccessResponse member = documentSyncService.checkAccess(memberId, documentId);

        assertThat(leader.teamId()).isEqualTo(teamId);
        assertThat(leader.leader()).isTrue();
        assertThat(member.memberId()).isEqualTo(memberId);
        assertThat(member.nickname()).startsWith("dk");
        assertThat(member.leader()).isFalse();
        assertThatThrownBy(() -> documentSyncService.checkAccess(outsiderId, documentId))
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_TEAM_MEMBER);
    }
}
