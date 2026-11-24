package com.gongbang.file.service;

import java.io.IOException;
import org.springframework.web.multipart.MultipartFile;

/** 업로드 파일 바이너리 저장소 추상화 (`local` 디스크 / `s3` Garage). */
public interface FileStorage {

    /** 주어진 {@code storageKey}(예: {@code chat/7/uuid.png}) 위치에 파일을 저장한다. */
    void store(String storageKey, String contentType, MultipartFile file) throws IOException;

    /** 저장 키를 브라우저에서 열 수 있는 공개 URL로 변환한다. */
    String resolveUrl(String storageKey);

    /** DB 저장 실패·트랜잭션 롤백 시 이미 저장한 객체를 조용히 정리한다. */
    void deleteQuietly(String storageKey);
}
