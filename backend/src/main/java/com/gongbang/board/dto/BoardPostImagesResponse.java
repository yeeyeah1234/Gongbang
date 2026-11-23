package com.gongbang.board.dto;

import com.gongbang.board.dto.BoardPostDetailResponse.ImageInfo;
import java.util.List;

/** BRD-006 사진 올리기·삭제·순서 변경 응답: 지금 남아 있는 사진 전체를 보여 주는 순서대로. */
public record BoardPostImagesResponse(List<ImageInfo> images) {}
