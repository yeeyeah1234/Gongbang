package com.gongbang.file.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** 로컬 개발용 디스크 저장소 (`app.upload-dir` 아래 저장, `/uploads/**`로 서빙). */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.profile-storage", havingValue = "local", matchIfMissing = true)
public class LocalFileStorage implements FileStorage {

    private static final String UPLOADS_PREFIX = "/uploads/";

    private final Path root;

    public LocalFileStorage(@Value("${app.upload-dir:./uploads}") String uploadDir) {
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    @Override
    public void store(String storageKey, String contentType, MultipartFile file)
            throws IOException {
        Path target = resolveSafePath(storageKey);
        Files.createDirectories(target.getParent());
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public String resolveUrl(String storageKey) {
        return UPLOADS_PREFIX + normalizeKey(storageKey);
    }

    @Override
    public void deleteQuietly(String storageKey) {
        try {
            Files.deleteIfExists(resolveSafePath(storageKey));
        } catch (IOException | RuntimeException e) {
            log.warn("Failed to delete local uploaded file: key={}", storageKey, e);
        }
    }

    private Path resolveSafePath(String storageKey) {
        String key = normalizeKey(storageKey);
        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root) || resolved.equals(root)) {
            throw new IllegalArgumentException("Invalid storage key: " + storageKey);
        }
        return resolved;
    }

    private static String normalizeKey(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("Storage key must not be blank");
        }
        String normalized = storageKey.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }
}
