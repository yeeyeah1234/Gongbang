package com.gongbang.file.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/** S3(Garage) 공개 버킷 기반 파일 저장소. */
@Slf4j
public class S3FileStorage implements FileStorage {

    private final S3Client client;
    private final String bucket;
    private final String publicBaseUrl;

    public S3FileStorage(S3Client client, String bucket, String publicBaseUrl) {
        this.client = client;
        this.bucket = bucket;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    @Override
    public void store(String storageKey, String contentType, MultipartFile file)
            throws IOException {
        String key = normalizeKey(storageKey);
        PutObjectRequest request =
                PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build();
        try (InputStream in = file.getInputStream()) {
            client.putObject(request, RequestBody.fromInputStream(in, file.getSize()));
        }
    }

    @Override
    public String resolveUrl(String storageKey) {
        return publicBaseUrl + "/" + normalizeKey(storageKey);
    }

    @Override
    public Optional<String> extractKey(String url) {
        String prefix = publicBaseUrl + "/";
        if (url == null || url.isBlank() || !url.startsWith(prefix)) {
            return Optional.empty();
        }
        String candidate = url.substring(prefix.length());
        if (candidate.contains("\\") || candidate.contains("?") || candidate.contains("#")) {
            return Optional.empty();
        }
        try {
            return Optional.of(normalizeKey(candidate));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @Override
    public void deleteQuietly(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return;
        }
        try {
            String key = normalizeKey(storageKey);
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (RuntimeException e) {
            log.warn("Failed to delete S3 uploaded file: key={}", storageKey, e);
        }
    }

    private static String normalizeKey(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("Storage key must not be blank");
        }
        String normalized = storageKey.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isBlank() || hasUnsafeSegment(normalized)) {
            throw new IllegalArgumentException("Invalid storage key: " + storageKey);
        }
        return normalized;
    }

    private static boolean hasUnsafeSegment(String key) {
        for (String segment : key.split("/", -1)) {
            if ("..".equals(segment) || ".".equals(segment)) {
                return true;
            }
        }
        return false;
    }
}
