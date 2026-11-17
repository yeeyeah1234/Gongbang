package com.gongbang.auth.repository;

import com.gongbang.auth.domain.RefreshToken;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** AUTH-02 ⑤: 모든 기기 로그아웃 */
    @Modifying
    @Query(
            "update RefreshToken r set r.revokedAt = :now"
                    + " where r.memberId = :memberId and r.revokedAt is null")
    int revokeAll(@Param("memberId") Long memberId, @Param("now") LocalDateTime now);

    /** MY-04 ⑤: 지금 기기를 뺀 나머지 로그아웃 */
    @Modifying
    @Query(
            "update RefreshToken r set r.revokedAt = :now"
                    + " where r.memberId = :memberId and r.revokedAt is null and r.id <> :keepId")
    int revokeAllExcept(
            @Param("memberId") Long memberId,
            @Param("keepId") Long keepId,
            @Param("now") LocalDateTime now);
}
