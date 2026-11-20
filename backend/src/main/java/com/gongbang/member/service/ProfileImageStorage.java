package com.gongbang.member.service;

import java.io.IOException;
import org.springframework.web.multipart.MultipartFile;

public interface ProfileImageStorage {
    String store(Long memberId, String extension, MultipartFile file) throws IOException;

    void delete(String url) throws IOException;
}
