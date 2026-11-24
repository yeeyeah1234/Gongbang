package com.gongbang.file.domain;

import java.util.Locale;
import java.util.Optional;

/** 공통 파일 업로드 용도 (`uploaded_file.purpose`). */
public enum FilePurpose {
    PROFILE,
    PROOF,
    CHAT,
    DOC,
    BOARD;

    /** 대소문자 무관하게 업로드 용도 문자열을 파싱한다. */
    public static Optional<FilePurpose> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(FilePurpose.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** 공개 URL 정책·비공개 저장소 분리가 준비된 용도만 허용한다 (`PROOF` 등 미구현 용도 차단). */
    public boolean isSupportedForUpload() {
        return this == CHAT;
    }

    /** 저장소 키 프리픽스로 사용할 소문자 폴더명을 반환한다. */
    public String folderName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
