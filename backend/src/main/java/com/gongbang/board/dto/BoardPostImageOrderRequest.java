package com.gongbang.board.dto;

import java.util.List;

/** BRD-006 사진 순서 변경: 남아 있는 사진 ID 전부를 새 순서대로 (첫 번째가 대표). */
public record BoardPostImageOrderRequest(List<Long> imageIds) {}
