package com.gongbang.auth.dto;

import com.gongbang.member.domain.Member;

public record LoginResponse(String accessToken, LoginMemberResponse member) {

    public record LoginMemberResponse(
            Long memberId, String nickname, String profileImageUrl, String role) {

        public static LoginMemberResponse from(Member member) {
            return new LoginMemberResponse(
                    member.getId(),
                    member.getNickname(),
                    member.getProfileImageUrl(),
                    member.getRole().name());
        }
    }

    public static LoginResponse from(LoginResult result) {
        return new LoginResponse(result.accessToken(), LoginMemberResponse.from(result.member()));
    }
}
