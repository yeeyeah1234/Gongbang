package com.gongbang.comment.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gongbang.auth.domain.LoginMember;
import com.gongbang.comment.dto.CommentAuthorResponse;
import com.gongbang.comment.dto.CommentCreateResponse;
import com.gongbang.comment.dto.CommentResponse;
import com.gongbang.comment.service.CommentCommandService;
import com.gongbang.comment.service.CommentQueryService;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.GlobalExceptionHandler;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.domain.MemberRole;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class CommentControllerTest {

    private static final Long MEMBER_ID = 7L;

    @Mock private CommentQueryService commentQueryService;
    @Mock private CommentCommandService commentCommandService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(
                                new CommentController(commentQueryService, commentCommandService))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                        .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static void login() {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                new LoginMember(MEMBER_ID, MemberRole.USER), null, List.of()));
    }

    @Test
    @DisplayName(
            "비회원도 커뮤니티 게시글 댓글 목록을 조회할 수 있고 JSON 필드명(isPostAuthor·isSecret·isDeleted)이 명세와 일치한다")
    void 비회원_커뮤니티_댓글_목록_조회() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 11, 24, 18, 0);
        CommentResponse item =
                new CommentResponse(
                        10L,
                        new CommentAuthorResponse(3L, "작성자닉", "/img.png"),
                        true,
                        "댓글 본문",
                        false,
                        false,
                        now,
                        now,
                        List.of());
        given(commentQueryService.getBoardPostComments(eq(5L), isNull())).willReturn(List.of(item));

        mockMvc.perform(get("/api/board-posts/5/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].commentId").value(10))
                .andExpect(jsonPath("$.data[0].isPostAuthor").value(true))
                .andExpect(jsonPath("$.data[0].isSecret").value(false))
                .andExpect(jsonPath("$.data[0].isDeleted").value(false));
    }

    @Test
    @DisplayName("로그인 회원이 커뮤니티 게시글 대댓글을 등록하면 commentId를 반환한다")
    void 커뮤니티_대댓글_작성() throws Exception {
        login();
        given(commentCommandService.createBoardPostComment(eq(5L), eq(MEMBER_ID), any()))
                .willReturn(new CommentCreateResponse(22L));

        mockMvc.perform(
                        post("/api/board-posts/5/comments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"content\":\"대댓글\",\"isSecret\":true,\"parentId\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.commentId").value(22));
    }

    @Test
    @DisplayName("2단계 이상 답글 작성 시 400 REPLY_DEPTH_EXCEEDED를 반환한다")
    void 답글_깊이_초과_400() throws Exception {
        login();
        given(commentCommandService.createPostComment(eq(9L), eq(MEMBER_ID), any()))
                .willThrow(new BusinessException(ErrorCode.REPLY_DEPTH_EXCEEDED));

        mockMvc.perform(
                        post("/api/posts/9/comments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"content\":\"대댓글의 대댓글\",\"isSecret\":false,\"parentId\":15}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("REPLY_DEPTH_EXCEEDED"));
    }

    @Test
    @DisplayName("PATCH·DELETE /api/comments/{commentId}는 로그인 회원 ID와 요청을 전달한다")
    void 댓글_수정_및_삭제() throws Exception {
        login();
        LocalDateTime now = LocalDateTime.of(2026, 11, 24, 18, 10);
        given(commentCommandService.updateComment(eq(10L), eq(MEMBER_ID), any()))
                .willReturn(
                        new CommentResponse(
                                10L,
                                new CommentAuthorResponse(MEMBER_ID, "건우", null),
                                false,
                                "수정됨",
                                true,
                                false,
                                now,
                                now,
                                List.of()));

        mockMvc.perform(
                        patch("/api/comments/10")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"content\":\"수정됨\",\"isSecret\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("수정됨"))
                .andExpect(jsonPath("$.data.isSecret").value(true));

        mockMvc.perform(delete("/api/comments/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        verify(commentCommandService).deleteComment(10L, MEMBER_ID);
    }
}
