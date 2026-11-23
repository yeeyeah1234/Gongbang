package com.gongbang.board.dto;

/** BRD-007 좋아요·취소 응답: 지금 좋아요 수와 내가 눌렀는지. */
public record BoardPostLikeResponse(int likeCount, boolean isLiked) {}
