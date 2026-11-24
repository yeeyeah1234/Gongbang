package com.gongbang.file.dto;

/** `POST /api/files` 파일 업로드 응답 (`{ fileId, url }`). */
public record FileUploadResponse(Long fileId, String url) {}
