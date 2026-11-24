package com.gongbang.comment.controller;

import com.gongbang.comment.service.CommentModerationService;
import com.gongbang.global.common.ApiResponse;
import com.gongbang.post.service.AdminPostService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class CommentReadController {
    private final CommentModerationService comments;
    private final AdminPostService posts;

    @GetMapping("/api/posts/{postId}/comments")
    public ApiResponse<?> list(@PathVariable Long postId) {
        posts.reportAuthor(postId);
        return ApiResponse.success(comments.list(postId));
    }
}
