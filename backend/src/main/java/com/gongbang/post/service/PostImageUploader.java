package com.gongbang.post.service;

import com.gongbang.file.service.AllowedImageType;
import com.gongbang.file.service.FileStorage;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * PST-007 공모전 사진·BRD-006 결과물 사진을 공용 파일 저장소({@link FileStorage}, #120)에 저장한다. 형식·크기·장수 검사는 부르는 서비스가
 * 먼저 한다.
 *
 * <p>저장 키는 {@code {폴더}/{글 ID}/{uuid}.{확장자}}로, 공용 저장소로 옮기기 전 S3 주소와 같은 모양이다. 이미 올린 사진은 DB의 주소를 그대로
 * 쓰고 사진 삭제는 소프트 삭제라 파일을 건드리지 않으므로, 예전 주소도 그대로 열린다.
 */
@Component
@RequiredArgsConstructor
public class PostImageUploader {

    // recruit_post_image.image_url · board_post_image.image_url 길이 (VARCHAR(500))
    static final int MAX_URL_LENGTH = 500;

    private final FileStorage fileStorage;

    /** 저장하고 화면에서 열 주소를 돌려준다. 트랜잭션 안에서 부르면, DB 저장이 되돌려질 때 방금 저장한 파일을 지운다 (남은 행이 없는 파일을 가리키지 않게). */
    public String store(ImageFolder folder, Long ownerId, AllowedImageType type, MultipartFile file)
            throws IOException {
        String key =
                folder.path() + "/" + ownerId + "/" + UUID.randomUUID() + "." + type.extension();
        String url = fileStorage.resolveUrl(key);
        if (url.length() > MAX_URL_LENGTH) {
            throw new IllegalStateException("Image URL exceeds column size");
        }
        fileStorage.store(key, type.contentType(), file);
        deleteOnRollback(key);
        return url;
    }

    private void deleteOnRollback(String key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status == STATUS_ROLLED_BACK) {
                            fileStorage.deleteQuietly(key);
                        }
                    }
                });
    }
}
