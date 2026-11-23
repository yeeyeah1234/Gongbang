package com.gongbang.member.dto;

import java.util.List;

/** 다른 도메인(chat·team)이 회원의 공개 요약 정보(닉네임·프로필·희망 역할)를 조회할 때 쓰는 DTO. */
public record MemberProfileSummary(
        Long memberId, String nickname, String profileImageUrl, List<String> desiredRoles) {}
