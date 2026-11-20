package com.gongbang.workspace.notice.dto;

/** 공지 작성자. 회원 행이 없으면(FK SET NULL) memberId는 null, 탈퇴·삭제 회원의 닉네임은 「탈퇴한 사용자」. */
public record NoticeAuthor(Long memberId, String nickname) {}
