package com.gongbang.post.service;

import java.io.IOException;
import org.springframework.web.multipart.MultipartFile;

/**
 * PST-007 공모전 사진·BRD-006 결과물 사진 저장소. 저장 위치는 프로필 사진(MY-01)과 같은 설정(app.profile-storage)을 따른다: local이면
 * app.upload-dir 폴더, s3이면 공개 버킷. 폴더(ImageFolder)로 두 기능의 파일을 나눈다.
 */
public interface PostImageStorage {

    /** 저장하고 화면에서 열 주소를 돌려준다. ownerId는 모집글·게시글 ID. */
    String store(ImageFolder folder, Long ownerId, PostImageType type, MultipartFile file)
            throws IOException;

    /** PST-007 모집글 공모전 사진. */
    default String store(Long postId, PostImageType type, MultipartFile file) throws IOException {
        return store(ImageFolder.POST, postId, type, file);
    }

    /** 저장한 파일을 지운다. DB 저장이 되돌려졌을 때만 쓴다 (사진 삭제는 소프트 삭제라 파일을 남긴다, ④). */
    void delete(String url) throws IOException;
}
