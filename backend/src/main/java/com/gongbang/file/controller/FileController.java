package com.gongbang.file.controller;

import com.gongbang.auth.domain.LoginMember;
import com.gongbang.file.dto.FileUploadResponse;
import com.gongbang.file.service.FileUploadService;
import com.gongbang.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 공통 파일 업로드 API 컨트롤러 (`POST /api/files`). */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/files")
public class FileController {

    private final FileUploadService fileUploadService;

    /** 파일을 업로드하고 `fileId`와 공개 접근 URL을 반환한다. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FileUploadResponse> upload(
            @AuthenticationPrincipal LoginMember me,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "purpose", required = false) String purpose) {
        return ApiResponse.success(fileUploadService.upload(me.getMemberId(), purpose, file));
    }
}
