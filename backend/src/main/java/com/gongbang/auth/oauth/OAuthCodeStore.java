package com.gongbang.auth.oauth;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * AUTH-10 ③ 1회용 키: 1분 유효, 쓰면 삭제 (AGENTS 9 `oauth:code:{키}`).
 *
 * <p>TODO(확인 필요): AGENTS 2-2는 Redis TTL로 정했지만 아직 Redis가 없어 서버 메모리에 둔다 (RateLimiter와
 * 같음). 서버가 2대(blue/green)면 Redis로 바꿔야 한다.
 */
@Component
public class OAuthCodeStore {

    private static final Duration TTL = Duration.ofMinutes(1);

    private final Map<String, Entry> codes = new ConcurrentHashMap<>();

    public String save(OAuthPending pending) {
        Instant now = Instant.now();
        codes.values().removeIf(entry -> entry.expiresAt().isBefore(now));
        String code = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID();
        codes.put(code, new Entry(pending, now.plus(TTL)));
        return code;
    }

    /** 한 번만 꺼낼 수 있다 */
    public Optional<OAuthPending> consume(String code) {
        if (code == null) {
            return Optional.empty();
        }
        Entry entry = codes.remove(code);
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }
        return Optional.of(entry.pending());
    }

    private record Entry(OAuthPending pending, Instant expiresAt) {}
}
