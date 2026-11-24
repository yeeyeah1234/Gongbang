package com.gongbang.chat.service;

import com.gongbang.file.service.FileUploadService;
import java.util.Collection;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 공용 파일 업로드 모듈(`FileUploadService`)에서 채팅 첨부 이미지의 접근 URL을 조회한다. */
@Component
@RequiredArgsConstructor
public class UploadedChatFileUrlProvider implements ChatFileUrlProvider {

    private final FileUploadService fileUploadService;

    @Override
    public Map<Long, String> getFileUrls(Collection<Long> fileIds) {
        return fileUploadService.getFileUrls(fileIds);
    }
}
