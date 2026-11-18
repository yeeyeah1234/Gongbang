package com.gongbang.contest.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

class ContestViewCookieTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Test
    @DisplayName("쿠키 값에서 번호만 읽고, 비었거나 깨진 조각은 건너뛴다")
    void 쿠키_값을_읽는다() {
        assertThat(ContestViewCookie.parse(null)).isEmpty();
        assertThat(ContestViewCookie.parse("")).isEmpty();
        assertThat(ContestViewCookie.parse("12.abc.402..7")).containsExactly(12L, 402L, 7L);
    }

    @Test
    @DisplayName("쿠키는 한국 시간 다음 날 0시에 만료된다")
    void 자정에_만료된다() {
        ZonedDateTime elevenPm = ZonedDateTime.of(2026, 11, 12, 23, 0, 0, 0, KST);

        ResponseCookie cookie = ContestViewCookie.build(List.of(1L, 2L), elevenPm);

        assertThat(cookie.getValue()).isEqualTo("1.2");
        assertThat(cookie.getMaxAge().getSeconds()).isEqualTo(3600);
        assertThat(cookie.isHttpOnly()).isTrue();
    }

    @Test
    @DisplayName("번호가 너무 많으면 오래된 것부터 빼고 최근 것만 남긴다")
    void 최근_번호만_남긴다() {
        List<Long> ids = LongStream.rangeClosed(1, ContestViewCookie.MAX_IDS + 5).boxed().toList();

        ResponseCookie cookie =
                ContestViewCookie.build(ids, ZonedDateTime.of(2026, 11, 12, 9, 0, 0, 0, KST));

        List<String> kept = List.of(cookie.getValue().split("\\."));
        assertThat(kept).hasSize(ContestViewCookie.MAX_IDS);
        assertThat(kept.get(0)).isEqualTo("6");
        assertThat(kept.get(kept.size() - 1))
                .isEqualTo(String.valueOf(ContestViewCookie.MAX_IDS + 5));
    }
}
