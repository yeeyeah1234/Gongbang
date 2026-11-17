package com.gongbang.auth.oauth;

import com.gongbang.member.domain.LoginType;

/** 소셜 로그인이 끝난 뒤 1회용 키에 묶어 두는 결과. memberId가 있으면 기존 회원, 없으면 처음 온 사람 */
public record OAuthPending(Long memberId, LoginType loginType, String providerId, String email) {

    public boolean isExistingMember() {
        return memberId != null;
    }
}
