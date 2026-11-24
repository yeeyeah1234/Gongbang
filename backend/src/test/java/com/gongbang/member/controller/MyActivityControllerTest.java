package com.gongbang.member.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.gongbang.application.controller.MyApplicationListController;
import com.gongbang.application.service.MyApplicationListService;
import com.gongbang.auth.domain.LoginMember;
import com.gongbang.board.controller.MyBoardPostController;
import com.gongbang.board.service.MyBoardPostService;
import com.gongbang.comment.controller.MyCommentController;
import com.gongbang.comment.service.MyCommentService;
import com.gongbang.global.common.PageResponse;
import com.gongbang.member.domain.MemberRole;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MyActivityControllerTest {
    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void activityRoutesIgnoreUserSuppliedMemberId() throws Exception {
        var comments = mock(MyCommentService.class);
        var applications = mock(MyApplicationListService.class);
        var board = mock(MyBoardPostService.class);
        when(comments.list(7L, 0, 10)).thenReturn(PageResponse.of(List.of(), 0, 10, 0));
        when(applications.list(7L, 0, 10)).thenReturn(PageResponse.of(List.of(), 0, 10, 0));
        when(board.list(7L, 0, 10)).thenReturn(PageResponse.of(List.of(), 0, 10, 0));
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                new LoginMember(7L, MemberRole.USER), null, List.of()));
        var mvc =
                MockMvcBuilders.standaloneSetup(
                                new MyCommentController(comments),
                                new MyApplicationListController(applications),
                                new MyBoardPostController(board))
                        .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                        .build();
        for (String path : List.of("comments", "applications", "board-posts")) {
            mvc.perform(get("/api/members/me/" + path).param("memberId", "999"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items").isArray())
                    .andExpect(jsonPath("$.data.totalCount").value(0));
        }
        verify(comments).list(7L, 0, 10);
        verify(applications).list(7L, 0, 10);
        verify(board).list(7L, 0, 10);
    }
}
