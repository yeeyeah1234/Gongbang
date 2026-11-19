package com.gongbang.post.dto;

import java.util.List;

/** MY-02 ③ 내 모집글 선택 삭제 (API 명세서 POST /api/members/me/posts/delete). */
public record MyPostDeleteRequest(List<Long> postIds) {}
