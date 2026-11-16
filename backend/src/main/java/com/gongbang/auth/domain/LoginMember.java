package com.gongbang.auth.domain;

import com.gongbang.member.domain.MemberRole;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 로그인한 회원 (AGENTS 8 「로그인 사용자 확인」). 인증 모듈은 "누구인지"까지만 알려 준다.
 *
 * <p>컨트롤러에서 {@code @AuthenticationPrincipal LoginMember loginMember}로 받고 {@code
 * loginMember.getMemberId()}로 꺼낸다. 로그인이 필요한 /api/**는 토큰이 없거나 만료되면 필터가 컨트롤러 앞에서 401 UNAUTHORIZED를
 * 보내므로 null 확인이 필요 없다. 팀원·팀장 여부는 각 service에서 memberId로 확인한다.
 */
@Getter
@RequiredArgsConstructor
public class LoginMember {

    private final Long memberId;
    private final MemberRole role;

    public boolean isAdmin() {
        return role == MemberRole.ADMIN;
    }
}
