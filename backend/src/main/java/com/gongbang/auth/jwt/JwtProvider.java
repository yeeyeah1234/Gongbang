package com.gongbang.auth.jwt;

import com.gongbang.member.domain.MemberRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * access 토큰과 인증 완료 토큰(verificationToken)을 만들고 읽는다.
 *
 * <p>TODO(확인 필요): access 유효 시간(지금 30분)과 refresh 방식은 정진우가 팀에 확정 공유 예정 (AGENTS 2-2).
 */
@Slf4j
@Component
public class JwtProvider {

    private static final String CLAIM_TYPE = "typ";
    private static final String TYPE_ACCESS = "access";

    private final SecretKey key;
    private final Duration accessTtl;

    public JwtProvider(
            @Value("${app.jwt.secret:}") String secret,
            @Value("${app.jwt.access-minutes:30}") long accessMinutes) {
        this.key = buildKey(secret);
        this.accessTtl = Duration.ofMinutes(accessMinutes);
    }

    private static SecretKey buildKey(String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            // 로컬 개발용: 서버를 켤 때마다 새 키 (다시 켜면 로그인이 풀림). 배포에서는 JWT_SECRET을 꼭 넣는다.
            log.warn("JWT_SECRET이 비어 있거나 32바이트보다 짧아 임시 키를 씁니다 (로컬 개발 전용)");
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            return Keys.hmacShaKeyFor(random);
        }
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(Long memberId, MemberRole role) {
        return create(
                String.valueOf(memberId),
                Map.of(CLAIM_TYPE, TYPE_ACCESS, "role", role.name()),
                accessTtl);
    }

    public Optional<AccessClaims> parseAccessToken(String token) {
        return parse(token)
                .filter(claims -> TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class)))
                .map(
                        claims ->
                                new AccessClaims(
                                        Long.valueOf(claims.getSubject()),
                                        MemberRole.valueOf(claims.get("role", String.class))));
    }

    /** 이메일 인증을 마친 뒤 다음 단계(가입·재설정·복원·추가 인증)에 내는 토큰. 서버가 아닌 곳에 저장하지 않는다. */
    public String createPurposeToken(String type, String email, Duration ttl) {
        return create(email, Map.of(CLAIM_TYPE, type), ttl);
    }

    /** 맞으면 이메일, 틀리거나 만료면 빈 값 */
    public Optional<String> parsePurposeToken(String token, String type) {
        return parse(token)
                .filter(claims -> type.equals(claims.get(CLAIM_TYPE, String.class)))
                .map(Claims::getSubject);
    }

    private String create(String subject, Map<String, ?> claims, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .claims(claims)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    private Optional<Claims> parse(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(
                    Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public record AccessClaims(Long memberId, MemberRole role) {}
}
