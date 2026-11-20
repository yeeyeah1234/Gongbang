package com.gongbang.member.service;

import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.domain.Member;
import com.gongbang.member.dto.MyProfileResponse;
import java.io.IOException;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** MY-01 ③ 프로필 사진 (JPG·PNG 5MB 이하). 설정에 따라 로컬 폴더 또는 S3 공개 버킷에 저장한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileImageService {

    public static final long MAX_BYTES = 5L * 1024 * 1024;
    public static final String URL_PREFIX = "/uploads/profile/";

    private final MemberService memberService;

    private final ProfileImageStorage storage;

    @Transactional
    public MyProfileResponse change(Long memberId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "사진 파일을 골라 주세요");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "사진은 5MB 이하만 올릴 수 있어요");
        }
        // 파일 이름·Content-Type은 믿지 않고 앞부분 바이트로 JPG·PNG인지 본다
        String ext = detectExtension(file);
        if (ext == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "JPG 또는 PNG 사진만 올릴 수 있어요");
        }

        Member member = memberService.getActiveMember(memberId);
        String oldUrl = member.getProfileImageUrl();
        String newUrl;
        try {
            newUrl = storage.store(memberId, ext, file);
        } catch (IOException | RuntimeException e) {
            log.warn("Profile image upload failed memberId={}", memberId);
            throw new BusinessException(
                    ErrorCode.INTERNAL_SERVER_ERROR, "사진을 올리지 못했어요. 잠시 후 다시 시도해 주세요");
        }
        boolean synchronizedTransaction =
                org.springframework.transaction.support.TransactionSynchronizationManager
                        .isSynchronizationActive();
        if (synchronizedTransaction) {
            org.springframework.transaction.support.TransactionSynchronizationManager
                    .registerSynchronization(
                            new org.springframework.transaction.support
                                    .TransactionSynchronization() {
                                @Override
                                public void afterCommit() {
                                    deleteOldFile(oldUrl);
                                }

                                @Override
                                public void afterCompletion(int status) {
                                    if (status == STATUS_ROLLED_BACK) deleteOldFile(newUrl);
                                }
                            });
        }
        try {
            member.changeProfileImage(newUrl);
            MyProfileResponse response = memberService.getMyProfile(memberId);
            if (!synchronizedTransaction) deleteOldFile(oldUrl);
            return response;
        } catch (RuntimeException e) {
            if (!synchronizedTransaction) deleteOldFile(newUrl);
            throw e;
        }
    }

    void deleteOldFile(String url) {
        try {
            storage.delete(url);
        } catch (IOException | RuntimeException e) {
            log.warn("Profile image cleanup failed");
        }
    }

    private static String detectExtension(MultipartFile file) {
        byte[] head = new byte[8];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.readNBytes(head, 0, head.length);
        } catch (IOException e) {
            return null;
        }
        if (read >= 3
                && (head[0] & 0xFF) == 0xFF
                && (head[1] & 0xFF) == 0xD8
                && (head[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (read >= 8
                && (head[0] & 0xFF) == 0x89
                && head[1] == 'P'
                && head[2] == 'N'
                && head[3] == 'G'
                && head[4] == 0x0D
                && head[5] == 0x0A
                && head[6] == 0x1A
                && head[7] == 0x0A) {
            return "png";
        }
        return null;
    }
}
