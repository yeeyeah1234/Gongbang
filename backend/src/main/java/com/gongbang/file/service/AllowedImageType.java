package com.gongbang.file.service;

import java.util.Optional;

/** 파일 매직 바이트 기반으로 허용된 이미지 형식(JPG·PNG·WEBP·GIF·AVIF)을 판별한다. */
public enum AllowedImageType {
    JPG("jpg", "image/jpeg"),
    PNG("png", "image/png"),
    WEBP("webp", "image/webp"),
    GIF("gif", "image/gif"),
    AVIF("avif", "image/avif");

    private final String extension;
    private final String contentType;

    AllowedImageType(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    public String extension() {
        return extension;
    }

    public String contentType() {
        return contentType;
    }

    /** 파일 앞부분 바이트만으로 실제 이미지 형식을 판별한다 (확장자·Content-Type 스푸핑 방지). */
    public static Optional<AllowedImageType> detect(byte[] header) {
        if (header == null || header.length < 3) {
            return Optional.empty();
        }
        if (isJpg(header)) {
            return Optional.of(JPG);
        }
        if (isPng(header)) {
            return Optional.of(PNG);
        }
        if (isGif(header)) {
            return Optional.of(GIF);
        }
        if (isWebp(header)) {
            return Optional.of(WEBP);
        }
        if (isAvif(header)) {
            return Optional.of(AVIF);
        }
        return Optional.empty();
    }

    private static boolean isJpg(byte[] b) {
        return (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
    }

    private static boolean isPng(byte[] b) {
        return b.length >= 8
                && (b[0] & 0xFF) == 0x89
                && b[1] == 'P'
                && b[2] == 'N'
                && b[3] == 'G'
                && b[4] == '\r'
                && b[5] == '\n'
                && b[6] == 0x1A
                && b[7] == '\n';
    }

    private static boolean isGif(byte[] b) {
        return b.length >= 6
                && b[0] == 'G'
                && b[1] == 'I'
                && b[2] == 'F'
                && b[3] == '8'
                && (b[4] == '7' || b[4] == '9')
                && b[5] == 'a';
    }

    private static boolean isWebp(byte[] b) {
        return b.length >= 12 && matchesAscii(b, 0, "RIFF") && matchesAscii(b, 8, "WEBP");
    }

    private static boolean isAvif(byte[] b) {
        if (b.length < 12 || !matchesAscii(b, 4, "ftyp")) {
            return false;
        }
        if (matchesAvifBrand(b, 8)) {
            return true;
        }
        long boxSize =
                ((long) (b[0] & 0xFF) << 24)
                        | ((long) (b[1] & 0xFF) << 16)
                        | ((long) (b[2] & 0xFF) << 8)
                        | (b[3] & 0xFF);
        int limit = boxSize >= 16 ? (int) Math.min(boxSize, b.length) : b.length;
        for (int offset = 16; offset + 4 <= limit; offset += 4) {
            if (matchesAvifBrand(b, offset)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesAvifBrand(byte[] b, int offset) {
        return matchesAscii(b, offset, "avif") || matchesAscii(b, offset, "avis");
    }

    private static boolean matchesAscii(byte[] b, int offset, String ascii) {
        if (offset + ascii.length() > b.length) {
            return false;
        }
        for (int i = 0; i < ascii.length(); i++) {
            if (b[offset + i] != (byte) ascii.charAt(i)) {
                return false;
            }
        }
        return true;
    }
}
