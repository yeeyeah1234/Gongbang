package com.gongbang.auth.dto;

import com.gongbang.member.domain.Member;
import java.time.Duration;

/** 서비스 → 컨트롤러 (refresh 원문은 쿠키로만 내보낸다) */
public record LoginResult(
        String accessToken,
        String rawRefreshToken,
        Duration refreshTtl,
        boolean rememberMe,
        Member member) {}
