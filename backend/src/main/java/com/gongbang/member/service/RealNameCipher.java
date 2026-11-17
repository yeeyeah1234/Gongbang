package com.gongbang.member.service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** MY-08 ⑤: 실명은 AES(GCM)로 암호화해서 저장한다 (DB 명세서 verification_request.real_name) */
@Slf4j
@Component
public class RealNameCipher {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec key;

    public RealNameCipher(@Value("${app.crypto.real-name-key:}") String rawKey) {
        byte[] bytes = rawKey == null ? new byte[0] : rawKey.getBytes(StandardCharsets.UTF_8);
        if (bytes.length != 16 && bytes.length != 24 && bytes.length != 32) {
            log.warn("REAL_NAME_KEY가 16·24·32바이트가 아니라 임시 키를 씁니다 (로컬 개발 전용)");
            bytes = new byte[32];
            RANDOM.nextBytes(bytes);
        }
        this.key = new SecretKeySpec(bytes, "AES");
    }

    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(encrypted, 0, out, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("실명 암호화 실패", e);
        }
    }
}
