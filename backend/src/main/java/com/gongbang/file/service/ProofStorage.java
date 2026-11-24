package com.gongbang.file.service;

import java.io.IOException;
import java.nio.file.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

/** 공개 uploads 경로와 별개인 비공개 저장소. DB 키에 저장 방식도 남겨 설정 전환 후 기존 파일을 찾는다. */
@Component
public class ProofStorage {
    private final Path root;
    private final String mode;
    private final String bucket;
    private final ObjectProvider<S3Client> clients;

    public ProofStorage(
            @Value("${app.proof-upload-dir:./private-proofs}") String directory,
            @Value("${app.upload-dir:./uploads}") String publicDirectory,
            @Value("${app.profile-storage:local}") String mode,
            @Value("${S3_PRIVATE_BUCKET:}") String bucket,
            @Value("${S3_PUBLIC_BUCKET:}") String publicBucket,
            ObjectProvider<S3Client> clients) {
        root = Paths.get(directory).toAbsolutePath().normalize();
        Path publicRoot = Paths.get(publicDirectory).toAbsolutePath().normalize();
        if (root.startsWith(publicRoot) || publicRoot.startsWith(root))
            throw new IllegalArgumentException("증빙 저장소는 공개 업로드 경로와 분리해야 합니다");
        if ("s3".equals(mode) && (bucket.isBlank() || bucket.equals(publicBucket)))
            throw new IllegalArgumentException("증빙용 비공개 버킷을 별도로 설정해야 합니다");
        this.mode = mode;
        this.bucket = bucket;
        this.clients = clients;
    }

    public String write(String key, byte[] bytes, String mime) throws IOException {
        if ("s3".equals(mode)) {
            clients.getObject()
                    .putObject(
                            PutObjectRequest.builder()
                                    .bucket(bucket)
                                    .key(key)
                                    .contentType(mime)
                                    .build(),
                            RequestBody.fromBytes(bytes));
            return "s3:" + key;
        }
        Path path = local(key);
        Files.createDirectories(path.getParent());
        try {
            Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            Files.deleteIfExists(path);
            throw e;
        }
        return "local:" + key;
    }

    public byte[] read(String key) throws IOException {
        if (key.startsWith("s3:"))
            return clients.getObject()
                    .getObjectAsBytes(
                            GetObjectRequest.builder().bucket(bucket).key(key.substring(3)).build())
                    .asByteArray();
        if (!key.startsWith("local:")) throw new IOException("지원하지 않는 파일 키");
        return Files.readAllBytes(local(key.substring(6)));
    }

    public void delete(String key) throws IOException {
        if (key.startsWith("s3:"))
            clients.getObject()
                    .deleteObject(
                            DeleteObjectRequest.builder()
                                    .bucket(bucket)
                                    .key(key.substring(3))
                                    .build());
        else if (key.startsWith("local:")) Files.deleteIfExists(local(key.substring(6)));
    }

    private Path local(String key) throws IOException {
        if (!key.matches("proof/[0-9a-f-]{36}")) throw new IOException("잘못된 파일 키");
        return root.resolve(key);
    }
}
