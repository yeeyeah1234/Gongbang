package com.gongbang.comment.dto;

/** 댓글 작성자 프로필 요약 정보. 삭제 마스킹된 댓글은 `null`로 내려간다. */
public record CommentAuthorResponse(Long memberId, String nickname, String profileImageUrl) {}
