package com.gongbang.member.service;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@ConditionalOnProperty(name = "app.profile-storage", havingValue = "local", matchIfMissing = true)
public class LocalProfileImageStorage implements ProfileImageStorage {
    private final Path directory;

    public LocalProfileImageStorage(@Value("${app.upload-dir:./uploads}") String root) {
        directory = Paths.get(root, "profile").toAbsolutePath().normalize();
    }

    public String store(Long id, String ext, MultipartFile file) throws IOException {
        Files.createDirectories(directory);
        String name = id + "-" + UUID.randomUUID() + "." + ext;
        Path target = directory.resolve(name);
        try {
            file.transferTo(target);
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(target);
            throw e;
        }
        return ProfileImageService.URL_PREFIX + name;
    }

    public void delete(String url) throws IOException {
        if (url == null || !url.startsWith(ProfileImageService.URL_PREFIX)) return;
        String name = url.substring(ProfileImageService.URL_PREFIX.length());
        if (!name.matches("[0-9]+-[0-9a-fA-F-]{36}\\.(png|jpg)")) return;
        Files.deleteIfExists(directory.resolve(name));
    }
}
