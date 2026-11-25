package com.gongbang.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
        properties = {
            "spring.autoconfigure.exclude=",
            "spring.datasource.url=${TEST_DATASOURCE_URL:}",
            "spring.datasource.username=${TEST_DATASOURCE_USERNAME:root}",
            "spring.datasource.password=${TEST_DATASOURCE_PASSWORD:}",
            "app.profile-storage=local"
        })
@EnabledIfEnvironmentVariable(named = "TEST_DATASOURCE_URL", matches = ".*_test.*")
@Transactional
class MemberNicknameSearchMysqlTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MemberService service;

    long member(String nickname, String status) {
        jdbc.update("INSERT INTO member (nickname, status) VALUES (?, ?)", nickname, status);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @Test
    void exactMatchWithNewestIdComesBeforeFiftyOlderPartialMatches() {
        String keyword = "q" + UUID.randomUUID().toString().substring(0, 5);
        var expected = new ArrayList<Long>();
        for (int i = 0; i < 55; i++) {
            long id = member(keyword + i, "ACTIVE");
            if (i < 49) expected.add(id);
        }
        long exact = member(keyword, "ACTIVE");
        expected.add(0, exact);
        assertThat(
                        service.findActiveMemberIdsByNickname(
                                " " + keyword.toUpperCase(java.util.Locale.ROOT) + " "))
                .containsExactlyElementsOf(expected);
    }

    @Test
    void excludesWithdrawnAndDeletedEvenWhenExact() {
        String keyword = "q" + UUID.randomUUID().toString().substring(0, 5);
        member(keyword, "WITHDRAWN");
        member(keyword + "d", "DELETED");
        long active = member(keyword + "a", "ACTIVE");
        assertThat(service.findActiveMemberIdsByNickname(keyword)).containsExactly(active);
    }

    @Test
    void percentAndUnderscoreAreLiteralCharacters() {
        String keyword = UUID.randomUUID().toString().substring(0, 5);
        member(keyword + "ab", "ACTIVE");
        long literal = member(keyword + "%_", "ACTIVE");
        assertThat(service.findActiveMemberIdsByNickname(keyword + "%_")).containsExactly(literal);
    }
}
