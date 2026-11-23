package com.gongbang.post.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * PST-007: app.upload-dir/post 폴더에 저장하고 /uploads/post/… 주소로 보여 준다 (global UploadConfig가
 * /uploads/**를 연다).
 */
@Component
@ConditionalOnProperty(name = "app.profile-storage", havingValue = "local", matchIfMissing = true)
public class LocalPostImageStorage implements PostImageStorage {

    static final String URL_PREFIX = "/uploads/post/";
    private static final String NAME_PATTERN = "[0-9]+-[0-9a-fA-F-]{36}\\.(jpg|png|webp|gif|avif)";

    private final Path directory;

    public LocalPostImageStorage(@Value("${app.upload-dir:./uploads}") String root) {
        directory = Paths.get(root, "post").toAbsolutePath().normalize();
    }

    @Override
    public String store(Long postId, PostImageType type, MultipartFile file) throws IOException {
        Files.createDirectories(directory);
        String name = postId + "-" + UUID.randomUUID() + "." + type.extension();
        Path target = directory.resolve(name);
        try {
            file.transferTo(target);
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(target);
            throw e;
        }
        return URL_PREFIX + name;
    }

    @Override
    public void delete(String url) throws IOException {
        if (url == null || !url.startsWith(URL_PREFIX)) {
            return;
        }
        String name = url.substring(URL_PREFIX.length());
        if (!name.matches(NAME_PATTERN)) {
            return;
        }
        Files.deleteIfExists(directory.resolve(name));
    }
}
