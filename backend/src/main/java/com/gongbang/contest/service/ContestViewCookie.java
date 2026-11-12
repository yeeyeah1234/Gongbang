package com.gongbang.contest.service;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.ResponseCookie;

/**
 * MAIN-02 ⑤ 조회수: 같은 브라우저는 하루에 한 번만 올린다. 오늘 본 공모전 번호를 쿠키에 담고, 쿠키는 한국 시간 자정에 만료된다.
 *
 * <p>로그인 기능 전이라 회원이 아니라 브라우저 기준이다. 쿠키를 지우거나 다른 브라우저로 열면 다시 오른다.
 */
public final class ContestViewCookie {

    public static final String NAME = "contest_viewed";

    // 쿠키 한 개는 4KB까지라 번호를 이만큼만 담는다 (넘으면 오래 본 것부터 뺀다)
    static final int MAX_IDS = 300;

    private static final String SEPARATOR = ".";

    private ContestViewCookie() {}

    /** 쿠키 값 "12.45.402"를 번호 목록으로 바꾼다. 비어 있거나 이상한 값은 건너뛴다. */
    public static LinkedHashSet<Long> parse(String value) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (value == null || value.isBlank()) {
            return ids;
        }
        for (String part : value.split("\\.")) {
            try {
                ids.add(Long.parseLong(part));
            } catch (NumberFormatException ignored) {
                // 사용자가 쿠키를 고쳤거나 깨진 경우: 그 조각만 버린다
            }
        }
        return ids;
    }

    /** 오늘 본 번호를 담은 쿠키. 다음 날 0시(now의 시간대 기준)에 만료된다. */
    public static ResponseCookie build(Collection<Long> ids, ZonedDateTime now) {
        List<Long> kept = new ArrayList<>(ids);
        if (kept.size() > MAX_IDS) {
            kept = kept.subList(kept.size() - MAX_IDS, kept.size());
        }
        String value = kept.stream().map(String::valueOf).collect(Collectors.joining(SEPARATOR));

        ZonedDateTime nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(now.getZone());
        long secondsUntilMidnight = Math.max(1, Duration.between(now, nextMidnight).getSeconds());

        return ResponseCookie.from(NAME, value)
                .path("/api/contests")
                .maxAge(secondsUntilMidnight)
                .httpOnly(true)
                .sameSite("Lax")
                .build();
    }
}
