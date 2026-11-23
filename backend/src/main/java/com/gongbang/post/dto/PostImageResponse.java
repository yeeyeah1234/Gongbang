package com.gongbang.post.dto;

import com.gongbang.post.domain.RecruitPostImage;

/** PST-007 공모전 사진 한 장 (API 명세서 images[]). */
public record PostImageResponse(Long imageId, String url) {

    public static PostImageResponse from(RecruitPostImage image) {
        return new PostImageResponse(image.getId(), image.getImageUrl());
    }
}
