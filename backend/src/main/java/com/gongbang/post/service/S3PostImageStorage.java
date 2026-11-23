package com.gongbang.post.service;

import java.io.IOException;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/** PST-007: 공개 버킷의 post/{postId}/ 아래에 저장한다 (프로필 사진 S3 저장과 같은 버킷·주소 규칙). */
public class S3PostImageStorage implements PostImageStorage {

    private static final String KEY_PATTERN =
            "post/[0-9]+/[0-9a-fA-F-]{36}\\.(jpg|png|webp|gif|avif)";
    private static final int MAX_URL_LENGTH = 500;

    private final S3Client client;
    private final String bucket;
    private final String base;

    public S3PostImageStorage(S3Client client, String bucket, String base) {
        this.client = client;
        this.bucket = bucket;
        this.base = base.replaceAll("/+$", "");
    }

    @Override
    public String store(Long postId, PostImageType type, MultipartFile file) throws IOException {
        String key = "post/" + postId + "/" + UUID.randomUUID() + "." + type.extension();
        String url = base + "/" + key;
        if (url.length() > MAX_URL_LENGTH) {
            throw new IllegalArgumentException("Post image URL exceeds column size");
        }
        client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(type.contentType())
                        .build(),
                RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        return url;
    }

    @Override
    public void delete(String url) {
        if (url == null || !url.startsWith(base + "/")) {
            return;
        }
        String key = url.substring(base.length() + 1);
        if (!key.matches(KEY_PATTERN)) {
            return;
        }
        client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }
}
