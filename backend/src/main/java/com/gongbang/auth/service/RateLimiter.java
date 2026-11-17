package com.gongbang.auth.service;

import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 잠깐 쓰고 사라지는 횟수 제한 (AGENTS 2-2: login:ip 1시간 30번, find-email:ip 1시간 10번).
 *
 * <p>TODO(확인 필요): AGENTS 2-2는 Redis(INCR + EXPIRE)로 정했지만 아직 Redis가 프로젝트에 없어, 같은 키·횟수로 서버 메모리에 둔다.
 * Redis를 붙이면 이 클래스 안만 바꾸면 된다(부르는 쪽은 그대로).
 */
@Component
public class RateLimiter {

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    /** 횟수를 1 올리고, 넘으면 429 */
    public void hit(String key, int limit, Duration window, String message) {
        Instant now = Instant.now();
        Window current =
                windows.compute(
                        key,
                        (k, old) ->
                                (old == null || old.expiresAt().isBefore(now))
                                        ? new Window(1, now.plus(window))
                                        : new Window(old.count() + 1, old.expiresAt()));
        if (current.count() > limit) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, message);
        }
        if (windows.size() > 10_000) {
            windows.values().removeIf(w -> w.expiresAt().isBefore(now));
        }
    }

    private record Window(int count, Instant expiresAt) {}
}
