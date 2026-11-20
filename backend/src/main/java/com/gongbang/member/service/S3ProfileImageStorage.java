package com.gongbang.member.service;

import java.io.IOException;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

public class S3ProfileImageStorage implements ProfileImageStorage {
    private final S3Client client;
    private final String bucket;
    private final String base;

    public S3ProfileImageStorage(S3Client client, String bucket, String base) {
        this.client = client;
        this.bucket = bucket;
        this.base = base.replaceAll("/+$", "");
    }

    public String store(Long id, String ext, MultipartFile file) throws IOException {
        String key = "profile/" + id + "/" + UUID.randomUUID() + "." + ext;
        String url = base + "/" + key;
        if (url.length() > 500)
            throw new IllegalArgumentException("Profile URL exceeds column size");
        client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(ext.equals("png") ? "image/png" : "image/jpeg")
                        .build(),
                RequestBody.fromBytes(file.getBytes()));
        return url;
    }

    public void delete(String url) {
        if (url == null || !url.startsWith(base + "/")) return;
        String key = url.substring(base.length() + 1);
        if (!key.matches("profile/[0-9]+/[0-9a-fA-F-]{36}\\.(png|jpg)")) return;
        client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }
}
