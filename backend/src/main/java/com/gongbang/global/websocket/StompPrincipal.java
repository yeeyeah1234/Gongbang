package com.gongbang.global.websocket;

import com.gongbang.member.domain.MemberRole;
import java.security.Principal;

/** STOMP 세션에 바인딩되는 인증 주체. */
public record StompPrincipal(Long memberId, MemberRole role) implements Principal {

    @Override
    public String getName() {
        return String.valueOf(memberId);
    }
}
