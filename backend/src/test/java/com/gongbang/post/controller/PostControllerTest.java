package com.gongbang.post.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gongbang.auth.domain.LoginMember;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.GlobalExceptionHandler;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.domain.MemberRole;
import com.gongbang.post.dto.PostCreateRequest;
import com.gongbang.post.dto.PostCreateResponse;
import com.gongbang.post.dto.PostUpdateRequest;
import com.gongbang.post.service.PostCommandService;
import com.gongbang.post.service.PostDetailService;
import com.gongbang.post.service.PostQueryService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 모집글 API 경로·파라미터 연결과 에러 응답 (PST-001~005). 보안 설정(로그인 필요 경로)은 실제 서버에서 확인하고, 여기서는 컨트롤러가 로그인 회원 ID와
 * 경로·본문을 service에 그대로 넘기는지 본다.
 */
@ExtendWith(MockitoExtension.class)
class PostControllerTest {

    private static final Long MEMBER_ID = 7L;

    @Mock private PostQueryService postQueryService;
    @Mock private PostDetailService postDetailService;
    @Mock private PostCommandService postCommandService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(
                                new PostController(
                                        postQueryService, postDetailService, postCommandService))
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
    @DisplayName("비회원이 상세를 열면 회원 ID 없이(null) 조회한다")
    void 비회원_상세() throws Exception {
        mockMvc.perform(get("/api/posts/10")).andExpect(status().isOk());

        verify(postDetailService).getPost(10L, null);
    }

    @Test
    @DisplayName("로그인 회원이 상세를 열면 그 회원 ID로 조회한다 (조회수·myStatus)")
    void 회원_상세() throws Exception {
        login();

        mockMvc.perform(get("/api/posts/10")).andExpect(status().isOk());

        verify(postDetailService).getPost(10L, MEMBER_ID);
    }

    @Test
    @DisplayName("작성: 본문을 읽어 로그인 회원 ID와 함께 넘기고 { postId }를 돌려준다")
    void 작성() throws Exception {
        login();
        given(postCommandService.create(eq(MEMBER_ID), any(PostCreateRequest.class)))
                .willReturn(new PostCreateResponse(55L));

        mockMvc.perform(
                        post("/api/posts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        { "contestId": 3, "title": "AI 팀원 모집", "targetCount": 4,
                                          "recruitStartDate": "2026-11-17", "recruitEndDate": "2026-12-01",
                                          "relatedUrl": null, "content": "내용" }
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.postId").value(55));

        ArgumentCaptor<PostCreateRequest> captor = ArgumentCaptor.forClass(PostCreateRequest.class);
        verify(postCommandService).create(eq(MEMBER_ID), captor.capture());
        PostCreateRequest request = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(request.contestId()).isEqualTo(3L);
        org.assertj.core.api.Assertions.assertThat(request.recruitEndDate())
                .isEqualTo(LocalDate.of(2026, 12, 1));
    }

    @Test
    @DisplayName("수정: 경로의 글 ID와 본문을 넘긴다")
    void 수정() throws Exception {
        login();

        mockMvc.perform(
                        patch("/api/posts/10")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{ \"title\": \"새 제목\" }"))
                .andExpect(status().isOk());

        verify(postCommandService)
                .update(
                        MEMBER_ID,
                        10L,
                        new PostUpdateRequest("새 제목", null, null, null, null, null));
    }

    @Test
    @DisplayName("삭제: 성공하면 data null")
    void 삭제() throws Exception {
        login();

        mockMvc.perform(delete("/api/posts/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(postCommandService).delete(MEMBER_ID, 10L);
    }

    @Test
    @DisplayName("service가 막으면 공통 응답 형식의 에러로 나간다 (예: 작성자 아님 403)")
    void 에러_응답() throws Exception {
        login();
        org.mockito.BDDMockito.willThrow(new BusinessException(ErrorCode.NOT_POST_AUTHOR))
                .given(postCommandService)
                .delete(MEMBER_ID, 10L);

        mockMvc.perform(delete("/api/posts/10"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("NOT_POST_AUTHOR"));
    }
}
