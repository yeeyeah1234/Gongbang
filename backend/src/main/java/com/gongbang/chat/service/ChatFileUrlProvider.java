package com.gongbang.chat.service;

import java.util.Collection;
import java.util.Map;

/**
 * 첨부 파일 ID를 접근 가능한 URL로 바꾸는 포트.
 *
 * <p>TODO(확인 필요): 공통 {@code uploaded_file} 모듈이 생기면 해당 서비스 구현체를 빈으로 연결한다.
 */
public interface ChatFileUrlProvider {

    /**
     * 여러 파일 ID에 대한 URL 매핑을 조회한다.
     *
     * @param fileIds 조회할 파일 ID 집합
     * @return {@code fileId -> URL} 맵 (없는 ID는 생략 가능)
     */
    Map<Long, String> getFileUrls(Collection<Long> fileIds);
}
