package com.gongbang.file.service;

import com.gongbang.file.domain.FilePurpose;
import com.gongbang.file.domain.UploadedFile;
import com.gongbang.file.repository.UploadedFileRepository;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.domain.MemberRole;
import com.gongbang.member.service.MemberService;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProofFileService {
    private final UploadedFileRepository files;
    private final ProofStorage storage;
    private final MemberService members;

    public record UploadResult(Long fileId, String url) {}

    public record Content(byte[] bytes, String mime) {}

    @Transactional
    public UploadResult upload(Long memberId, String purpose, MultipartFile file) {
        members.getActiveMember(memberId);
        if (!"PROOF".equals(purpose)
                || file == null
                || file.isEmpty()
                || file.getSize() > 5L * 1024 * 1024)
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT, "증빙은 JPG·PNG·PDF, 5MB 이하만 올릴 수 있어요");
        String key = null;
        try {
            byte[] bytes = file.getBytes();
            String mime = detect(bytes);
            if (mime == null)
                throw new BusinessException(ErrorCode.INVALID_INPUT, "JPG·PNG·PDF 파일만 올릴 수 있어요");
            key = storage.write("proof/" + UUID.randomUUID(), bytes, mime);
            String savedKey = key;
            if (TransactionSynchronizationManager.isSynchronizationActive())
                TransactionSynchronizationManager.registerSynchronization(
                        new TransactionSynchronization() {
                            @Override
                            public void afterCompletion(int status) {
                                if (status == STATUS_ROLLED_BACK) cleanup(savedKey);
                            }
                        });
            String name =
                    file.getOriginalFilename() == null
                            ? "증빙 파일"
                            : file.getOriginalFilename().replaceAll("[\\r\\n\\\\/]", "_");
            if (name.length() > 255) name = name.substring(0, 255);
            UploadedFile saved =
                    files.save(
                            UploadedFile.create(
                                    memberId, FilePurpose.PROOF, key, name, mime, bytes.length));
            return new UploadResult(saved.getId(), "/api/files/" + saved.getId());
        } catch (IOException e) {
            if (key != null) cleanup(key);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "증빙 파일을 저장하지 못했어요");
        } catch (RuntimeException e) {
            if (key != null) cleanup(key);
            throw e;
        }
    }

    private void cleanup(String key) {
        try {
            storage.delete(key);
        } catch (Exception e) {
            log.warn("증빙 저장 실패 후 파일 정리 실패");
        }
    }

    @Transactional(readOnly = true)
    public void requireOwned(Long fileId, Long ownerId) {
        if (!available(fileId, ownerId))
            throw new BusinessException(ErrorCode.PROOF_REQUIRED, "본인이 업로드한 증빙 파일을 선택해 주세요");
    }

    @Transactional(readOnly = true)
    public boolean available(Long fileId, Long ownerId) {
        return fileId != null
                && files.findById(fileId)
                        .filter(
                                f ->
                                        FilePurpose.PROOF == f.getPurpose()
                                                && f.getUploaderId().equals(ownerId))
                        .isPresent();
    }

    @Transactional(readOnly = true)
    public Content read(Long fileId, Long readerId) {
        var reader = members.getActiveMember(readerId);
        var file =
                files.findById(fileId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!file.getUploaderId().equals(readerId) && reader.getRole() != MemberRole.ADMIN)
            throw new BusinessException(ErrorCode.NOT_OWNER);
        if (file.getPurpose() != FilePurpose.PROOF)
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        try {
            return new Content(storage.read(file.getStorageKey()), file.getContentType());
        } catch (IOException | RuntimeException e) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "증빙 파일을 불러오지 못했어요");
        }
    }

    static String detect(byte[] b) {
        if (b.length >= 3 && (b[0] & 255) == 255 && (b[1] & 255) == 216 && (b[2] & 255) == 255)
            return "image/jpeg";
        if (b.length >= 8
                && java.util.Arrays.equals(
                        java.util.Arrays.copyOf(b, 8),
                        new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10})) return "image/png";
        if (b.length >= 5
                && new String(b, 0, 5, java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"))
            return "application/pdf";
        return null;
    }
}
