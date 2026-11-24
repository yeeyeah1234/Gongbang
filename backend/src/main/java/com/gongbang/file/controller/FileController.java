package com.gongbang.file.controller;

import com.gongbang.auth.domain.LoginMember;
import com.gongbang.file.domain.FilePurpose;
import com.gongbang.file.dto.FileUploadResponse;
import com.gongbang.file.service.FileUploadService;
import com.gongbang.file.service.ProofFileService;
import com.gongbang.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    private final ProofFileService proofFileService;

    /** 파일을 업로드하고 `fileId`와 공개 접근 URL을 반환한다. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FileUploadResponse> upload(
            @AuthenticationPrincipal LoginMember me,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "purpose", required = false) String purpose) {
        if (FilePurpose.parse(purpose).orElse(null) == FilePurpose.PROOF) {
            var result = proofFileService.upload(me.getMemberId(), "PROOF", file);
            return ApiResponse.success(new FileUploadResponse(result.fileId(), result.url()));
        }
        return ApiResponse.success(fileUploadService.upload(me.getMemberId(), purpose, file));
    }

    @GetMapping("/{id}")
    public ResponseEntity<byte[]> read(
            @AuthenticationPrincipal LoginMember me, @PathVariable Long id) {
        var result = proofFileService.read(id, me.getMemberId());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox")
                .header("Content-Disposition", "inline")
                .contentType(MediaType.parseMediaType(result.mime()))
                .body(result.bytes());
    }
}
