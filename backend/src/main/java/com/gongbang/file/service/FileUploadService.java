package com.gongbang.file.service;

import com.gongbang.file.domain.FilePurpose;
import com.gongbang.file.domain.UploadedFile;
import com.gongbang.file.dto.FileUploadResponse;
import com.gongbang.file.repository.UploadedFileRepository;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/** 공통 파일 업로드·URL 조회·채팅 첨부 검증 서비스 (`POST /api/files`). */
@Service
@RequiredArgsConstructor
public class FileUploadService {

    public static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;
    private static final int MAX_ORIGINAL_NAME_LENGTH = 255;

    private final UploadedFileRepository uploadedFileRepository;
    private final FileStorage fileStorage;

    /** 파일을 검증·저장하고 {@code uploaded_file} 메타데이터와 공개 URL을 반환한다. */
    @Transactional
    public FileUploadResponse upload(Long uploaderId, String rawPurpose, MultipartFile file) {
        FilePurpose purpose =
                FilePurpose.parse(rawPurpose)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.INVALID_INPUT, "파일 업로드 용도를 확인해 주세요."));
        if (!purpose.isSupportedForUpload()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "현재는 채팅 이미지 업로드만 지원해요.");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "업로드할 파일을 선택해 주세요.");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }

        AllowedImageType imageType = detectImageType(file);
        String storageKey =
                purpose.folderName()
                        + "/"
                        + uploaderId
                        + "/"
                        + UUID.randomUUID()
                        + "."
                        + imageType.extension();
        String originalName = sanitizeOriginalName(file.getOriginalFilename(), imageType);

        storeBinary(storageKey, imageType.contentType(), file);
        registerRollbackCleanup(storageKey);

        try {
            UploadedFile saved =
                    uploadedFileRepository.save(
                            UploadedFile.create(
                                    uploaderId,
                                    purpose,
                                    storageKey,
                                    originalName,
                                    imageType.contentType(),
                                    file.getSize()));
            return new FileUploadResponse(
                    saved.getId(), fileStorage.resolveUrl(saved.getStorageKey()));
        } catch (RuntimeException e) {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                fileStorage.deleteQuietly(storageKey);
            }
            throw e;
        }
    }

    /** 여러 {@code uploaded_file.id}에 대응하는 접근 URL 맵을 반환한다. */
    @Transactional(readOnly = true)
    public Map<Long, String> getFileUrls(Collection<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> result = new LinkedHashMap<>();
        for (UploadedFile file : uploadedFileRepository.findAllById(fileIds)) {
            result.put(file.getId(), fileStorage.resolveUrl(file.getStorageKey()));
        }
        return result;
    }

    /** 채팅 `IMAGE` 메시지 전송 전 첨부 파일의 존재·용도(`CHAT`)·업로더 본인을 검증한다. */
    @Transactional(readOnly = true)
    public void requireOwnedChatFile(Long fileId, Long uploaderId) {
        UploadedFile file =
                uploadedFileRepository
                        .findById(fileId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.RESOURCE_NOT_FOUND,
                                                "첨부 이미지 파일을 찾을 수 없어요."));
        if (file.getPurpose() != FilePurpose.CHAT) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "채팅에 첨부할 수 없는 파일입니다.");
        }
        if (!file.getUploaderId().equals(uploaderId)) {
            throw new BusinessException(ErrorCode.NOT_OWNER, "본인이 업로드한 이미지만 전송할 수 있어요.");
        }
    }

    private AllowedImageType detectImageType(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return AllowedImageType.detect(in)
                    .orElseThrow(() -> new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE));
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "업로드할 파일을 읽을 수 없어요.");
        }
    }

    private void storeBinary(String storageKey, String contentType, MultipartFile file) {
        try {
            fileStorage.store(storageKey, contentType, file);
        } catch (IOException | RuntimeException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "파일을 저장하지 못했어요.");
        }
    }

    private void registerRollbackCleanup(String storageKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                            fileStorage.deleteQuietly(storageKey);
                        }
                    }
                });
    }

    private static String sanitizeOriginalName(String rawName, AllowedImageType imageType) {
        if (rawName == null || rawName.isBlank()) {
            return "image." + imageType.extension();
        }
        String normalized = rawName.replace('\\', '/');
        int lastSlash = normalized.lastIndexOf('/');
        String baseName = lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;
        StringBuilder cleaned = new StringBuilder(baseName.length());
        for (int i = 0; i < baseName.length(); i++) {
            char ch = baseName.charAt(i);
            if (ch >= 0x20 && ch != 0x7F) {
                cleaned.append(ch);
            }
        }
        String candidate = cleaned.toString().trim();
        if (candidate.isEmpty()) {
            return "image." + imageType.extension();
        }
        if (candidate.length() <= MAX_ORIGINAL_NAME_LENGTH) {
            return candidate;
        }
        return candidate.substring(candidate.length() - MAX_ORIGINAL_NAME_LENGTH);
    }
}
