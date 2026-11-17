package com.gongbang.member.service;

import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.domain.Member;
import com.gongbang.member.dto.MyProfileResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * MY-01 ③ 프로필 사진 (JPG·PNG 5MB 이하). 파일 업로드 모듈이 생기기 전까지 서버 폴더(app.upload-dir)에 저장하고
 * /uploads/profile/... 주소로 보여 준다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileImageService {

    public static final long MAX_BYTES = 5L * 1024 * 1024;
    public static final String URL_PREFIX = "/uploads/profile/";

    private final MemberService memberService;

    @Value("${app.upload-dir:./uploads}")
    private String uploadDir;

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
        String fileName = memberId + "-" + UUID.randomUUID() + "." + ext;
        Path dir = profileDir();
        try {
            Files.createDirectories(dir);
            file.transferTo(dir.resolve(fileName));
        } catch (IOException e) {
            log.warn("프로필 사진 저장 실패 memberId={}", memberId);
            throw new BusinessException(
                    ErrorCode.INTERNAL_SERVER_ERROR, "사진을 올리지 못했어요. 잠시 후 다시 시도해 주세요");
        }
        deleteOldFile(member.getProfileImageUrl());
        member.changeProfileImage(URL_PREFIX + fileName);
        return memberService.getMyProfile(memberId);
    }

    private Path profileDir() {
        return Paths.get(uploadDir, "profile").toAbsolutePath().normalize();
    }

    /** 예전 사진이 우리 서버에 올린 파일이면 지운다 (다른 주소는 건드리지 않음) */
    void deleteOldFile(String oldUrl) {
        if (oldUrl == null || !oldUrl.startsWith(URL_PREFIX)) {
            return;
        }
        String name = oldUrl.substring(URL_PREFIX.length());
        if (name.contains("/") || name.contains("\\") || name.contains("..")) {
            return;
        }
        try {
            Files.deleteIfExists(profileDir().resolve(name));
        } catch (IOException e) {
            log.warn("예전 프로필 사진 삭제 실패");
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
        if (read >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
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
