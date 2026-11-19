package com.gongbang.post.controller;

import com.gongbang.auth.domain.LoginMember;
import com.gongbang.global.common.ApiResponse;
import com.gongbang.global.common.PageResponse;
import com.gongbang.post.dto.MyPostDeleteRequest;
import com.gongbang.post.dto.MyPostDeleteResponse;
import com.gongbang.post.dto.MyPostItemResponse;
import com.gongbang.post.service.PostCommandService;
import com.gongbang.post.service.PostQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 마이페이지 「내 글 관리」 모집글 탭 (MY-02, INT-002 ③). 경로는 마이페이지(정진우) 아래지만 모집글 데이터라 모집글 도메인에 둔다. 값 검증과 권한 확인은
 * service가 한다.
 */
@RestController
@RequiredArgsConstructor
public class MyPostController {

    private final PostQueryService postQueryService;
    private final PostCommandService postCommandService;

    /** MY-02 ②④: 로그인. 내가 쓴 모집글을 최신순으로, 10개씩. */
    @GetMapping("/api/members/me/posts")
    public ApiResponse<PageResponse<MyPostItemResponse>> getMyPosts(
            @AuthenticationPrincipal LoginMember me,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(postQueryService.getMyPosts(me.getMemberId(), page, size));
    }

    /** MY-02 ③⑥: 로그인. 고른 글을 PST-004와 같은 규칙으로 소프트 삭제한다. */
    @PostMapping("/api/members/me/posts/delete")
    public ApiResponse<MyPostDeleteResponse> deleteMyPosts(
            @AuthenticationPrincipal LoginMember me, @RequestBody MyPostDeleteRequest request) {
        return ApiResponse.success(
                postCommandService.deleteMine(me.getMemberId(), request.postIds()));
    }
}
