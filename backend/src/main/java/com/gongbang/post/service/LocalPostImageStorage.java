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
 * PST-007·BRD-006: app.upload-dir/{post|board} 폴더에 저장하고 /uploads/{post|board}/… 주소로 보여 준다 (global
 * UploadConfig가 /uploads/**를 연다).
 */
@Component
@ConditionalOnProperty(name = "app.profile-storage", havingValue = "local", matchIfMissing = true)
public class LocalPostImageStorage implements PostImageStorage {

    static final String URL_PREFIX = "/uploads/post/";
    private static final String UPLOADS = "/uploads/";
    private static final String NAME_PATTERN = "[0-9]+-[0-9a-fA-F-]{36}\\.(jpg|png|webp|gif|avif)";

    private final Path root;

    public LocalPostImageStorage(@Value("${app.upload-dir:./uploads}") String root) {
        this.root = Paths.get(root).toAbsolutePath().normalize();
    }

    @Override
    public String store(ImageFolder folder, Long ownerId, PostImageType type, MultipartFile file)
            throws IOException {
        Path directory = root.resolve(folder.path());
        Files.createDirectories(directory);
        String name = ownerId + "-" + UUID.randomUUID() + "." + type.extension();
        Path target = directory.resolve(name);
        try {
            file.transferTo(target);
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(target);
            throw e;
        }
        return UPLOADS + folder.path() + "/" + name;
    }

    @Override
    public void delete(String url) throws IOException {
        if (url == null || !url.startsWith(UPLOADS)) {
            return;
        }
        String[] parts = url.substring(UPLOADS.length()).split("/", -1);
        if (parts.length != 2
                || !ImageFolder.isKnown(parts[0])
                || !parts[1].matches(NAME_PATTERN)) {
            return;
        }
        Files.deleteIfExists(root.resolve(parts[0]).resolve(parts[1]));
    }
}
