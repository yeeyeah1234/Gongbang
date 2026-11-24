package com.gongbang.file.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gongbang.auth.domain.LoginMember;
import com.gongbang.file.dto.FileUploadResponse;
import com.gongbang.file.service.FileUploadService;
import com.gongbang.file.service.ProofFileService;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.GlobalExceptionHandler;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.domain.MemberRole;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class FileControllerTest {

    private static final Long MEMBER_ID = 7L;

    @Mock private FileUploadService fileUploadService;
    @Mock private ProofFileService proofFileService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(
                                new FileController(fileUploadService, proofFileService))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                        .build();
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                new LoginMember(MEMBER_ID, MemberRole.USER), null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void proofUsesPrivateStorage() throws Exception {
        MockMultipartFile file =
                new MockMultipartFile(
                        "file", "proof.pdf", "application/pdf", "%PDF-1.7".getBytes());
        given(proofFileService.upload(eq(MEMBER_ID), eq("PROOF"), any()))
                .willReturn(new ProofFileService.UploadResult(66L, "/api/files/66"));
        mockMvc.perform(multipart("/api/files").file(file).param("purpose", "PROOF"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileId").value(66))
                .andExpect(jsonPath("$.data.url").value("/api/files/66"));
        org.mockito.Mockito.verifyNoInteractions(fileUploadService);
    }

    @Test
    @DisplayName("POST /api/files는 multipart 파일과 purpose를 받아 fileId와 url을 반환한다")
    void 업로드_성공() throws Exception {
        MockMultipartFile file =
                new MockMultipartFile("file", "photo.png", "image/png", new byte[] {1, 2, 3});
        given(fileUploadService.upload(eq(MEMBER_ID), eq("CHAT"), any()))
                .willReturn(new FileUploadResponse(55L, "/uploads/chat/7/uuid.png"));

        mockMvc.perform(multipart("/api/files").file(file).param("purpose", "CHAT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fileId").value(55))
                .andExpect(jsonPath("$.data.url").value("/uploads/chat/7/uuid.png"));
    }

    @Test
    @DisplayName("용량 초과(FILE_TOO_LARGE) 시 400과 명세 에러 코드를 반환한다")
    void 용량_초과_400() throws Exception {
        MockMultipartFile file =
                new MockMultipartFile("file", "big.png", "image/png", new byte[] {1, 2, 3});
        given(fileUploadService.upload(eq(MEMBER_ID), eq("CHAT"), any()))
                .willThrow(new BusinessException(ErrorCode.FILE_TOO_LARGE));

        mockMvc.perform(multipart("/api/files").file(file).param("purpose", "CHAT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("FILE_TOO_LARGE"));
    }

    @Test
    @DisplayName("지원하지 않는 형식(UNSUPPORTED_FILE_TYPE) 시 400과 명세 에러 코드를 반환한다")
    void 미지원_형식_400() throws Exception {
        MockMultipartFile file =
                new MockMultipartFile("file", "bad.svg", "image/svg+xml", new byte[] {1, 2, 3});
        given(fileUploadService.upload(eq(MEMBER_ID), eq("CHAT"), any()))
                .willThrow(new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE));

        mockMvc.perform(multipart("/api/files").file(file).param("purpose", "CHAT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_FILE_TYPE"));
    }

    @Test
    @DisplayName("file 파트가 빠져도 500 대신 400 INVALID_INPUT으로 응답한다")
    void 파일_누락_400() throws Exception {
        given(fileUploadService.upload(eq(MEMBER_ID), eq("CHAT"), isNull()))
                .willThrow(new BusinessException(ErrorCode.INVALID_INPUT, "업로드할 파일을 선택해 주세요."));

        mockMvc.perform(multipart("/api/files").param("purpose", "CHAT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }
}
