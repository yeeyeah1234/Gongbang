package com.gongbang.post.dto;

import java.util.List;

/** PST-007 사진 올리기·삭제·순서 변경 응답: 지금 남아 있는 사진 전체를 보여 주는 순서대로. */
public record PostImagesResponse(List<PostImageResponse> images) {}
