package com.gongbang.post.dto;

import java.util.List;

/** PST-007 사진 순서 변경: 남아 있는 사진 ID 전부를 새 순서대로 (첫 번째가 대표). */
public record PostImageOrderRequest(List<Long> imageIds) {}
