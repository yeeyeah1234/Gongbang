package com.gongbang.post.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gongbang.post.domain.RecruitPost;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * NET-02 팀 초대 모달용 내 모집중 글(getMyRecruitingPosts)이 실제 MySQL에서 맞는 글만 최신순으로 고르는지 확인한다.
 *
 * <p>환경 변수 TEST_DATASOURCE_URL이 있을 때만 돈다 (PostDecreaseCountMysqlTest와 같은 방식). 반드시 테스트 전용 스키마(이름에
 * _test)를 가리키게 한다. 테스트가 끝나면 이 테스트가 넣은 공모전·모집글 행을 지운다.
 */
@SpringBootTest(
        properties = {
            // test application.yml이 끈 DataSource·JPA·Flyway 자동 설정을 이 테스트에서만 다시 켠다.
            "spring.autoconfigure.exclude=",
            "spring.datasource.url=${TEST_DATASOURCE_URL:}",
            "spring.datasource.username=${TEST_DATASOURCE_USERNAME:root}",
            "spring.datasource.password=${TEST_DATASOURCE_PASSWORD:}",
            "spring.jpa.properties.hibernate.show_sql=false"
        })
@EnabledIfEnvironmentVariable(named = "TEST_DATASOURCE_URL", matches = ".*_test.*")
class MyRecruitingPostsMysqlTest {

    // 다른 테스트 데이터와 겹치지 않게 큰 번호를 쓴다
    private static final long AUTHOR_ID = 970_001L;
    private static final long OTHER_ID = 970_002L;

    @Autowired private PostQueryService postQueryService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private long contestId;
    private long older;
    private long newer;
    private long periodEnded;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update(
                "INSERT INTO contest (source_url, source_site, title, category_id, end_date)"
                        + " VALUES ('https://test.local/my-recruiting', 'WEVITY', '내 모집중 공모전', 1,"
                        + " '2099-12-31')");
        contestId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        older = insert(AUTHOR_ID, "RECRUITING", "2099-12-01", "2026-11-01 10:00:00");
        newer = insert(AUTHOR_ID, "RECRUITING", "2099-12-01", "2026-11-03 10:00:00");
        // 종료일이 지난 「모집마감」 글도 저장된 상태는 모집중이라 넣는다
        periodEnded = insert(AUTHOR_ID, "RECRUITING", "2000-01-01", "2026-11-02 10:00:00");
        insert(AUTHOR_ID, "COMPLETED", "2099-12-01", "2026-11-04 10:00:00");
        long deleted = insert(AUTHOR_ID, "RECRUITING", "2099-12-01", "2026-11-05 10:00:00");
        jdbcTemplate.update("UPDATE recruit_post SET deleted_at = NOW() WHERE id = ?", deleted);
        insert(OTHER_ID, "RECRUITING", "2099-12-01", "2026-11-06 10:00:00");
    }

    private long insert(long authorId, String status, String endDate, String createdAt) {
        jdbcTemplate.update(
                "INSERT INTO recruit_post (author_id, contest_id, title, content, target_count, status,"
                        + " recruit_start_date, recruit_end_date, created_at)"
                        + " VALUES (?, ?, '내 모집중 테스트', '내용', 4, ?, '2026-11-01', ?, ?)",
                authorId,
                contestId,
                status,
                endDate,
                createdAt);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM recruit_post WHERE contest_id = ?", contestId);
        jdbcTemplate.update("DELETE FROM contest WHERE id = ?", contestId);
    }

    @Test
    @DisplayName("내가 쓴 모집중 글만 최신순: 모집완료·삭제된 글·다른 사람 글은 빼고, 모집마감(종료일 지남) 글은 넣는다")
    void 내_모집중_글만_최신순() {
        assertThat(postQueryService.getMyRecruitingPosts(AUTHOR_ID))
                .extracting(RecruitPost::getId)
                .containsExactly(newer, periodEnded, older);
    }
}
