package com.gongbang.post.service;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/** PST-007 ②: 올릴 수 있는 사진 형식 (브라우저가 라이브러리 없이 띄우는 형식). 파일 이름·Content-Type은 믿지 않고 파일 앞부분 바이트로 판단한다. */
enum PostImageType {
    JPG("jpg", "image/jpeg"),
    PNG("png", "image/png"),
    WEBP("webp", "image/webp"),
    GIF("gif", "image/gif"),
    AVIF("avif", "image/avif");

    /** 판단에 필요한 앞부분 길이. AVIF의 ftyp 상자에서 호환 브랜드까지 본다. */
    static final int HEAD_BYTES = 32;

    private static final byte[] PNG_SIGNATURE = {
        (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'
    };

    private final String extension;
    private final String contentType;

    PostImageType(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    String extension() {
        return extension;
    }

    String contentType() {
        return contentType;
    }

    /** 파일 앞부분(head)으로 형식을 판단한다. 아니면 비어 있다. */
    static Optional<PostImageType> detect(byte[] head) {
        if (head == null) {
            return Optional.empty();
        }
        if (startsWith(head, 0, new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})) {
            return Optional.of(JPG);
        }
        if (startsWith(head, 0, PNG_SIGNATURE)) {
            return Optional.of(PNG);
        }
        if (startsWith(head, 0, ascii("GIF87a")) || startsWith(head, 0, ascii("GIF89a"))) {
            return Optional.of(GIF);
        }
        if (startsWith(head, 0, ascii("RIFF")) && startsWith(head, 8, ascii("WEBP"))) {
            return Optional.of(WEBP);
        }
        if (isAvif(head)) {
            return Optional.of(AVIF);
        }
        return Optional.empty();
    }

    /**
     * AVIF는 ISO BMFF: 4바이트 상자 크기 + "ftyp" + 주 브랜드(4) + 버전(4) + 호환 브랜드들. 주 브랜드나 앞부분 안의 호환 브랜드가
     * avif(정지)·avis(움직임)면 AVIF로 본다.
     */
    private static boolean isAvif(byte[] head) {
        if (head.length < 16 || !startsWith(head, 4, ascii("ftyp"))) {
            return false;
        }
        long boxSize =
                ((head[0] & 0xFFL) << 24)
                        | ((head[1] & 0xFFL) << 16)
                        | ((head[2] & 0xFFL) << 8)
                        | (head[3] & 0xFFL);
        int end = (int) Math.min(boxSize, head.length);
        if (isAvifBrand(head, 8)) {
            return true;
        }
        for (int offset = 16; offset + 4 <= end; offset += 4) {
            if (isAvifBrand(head, offset)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAvifBrand(byte[] head, int offset) {
        return startsWith(head, offset, ascii("avif")) || startsWith(head, offset, ascii("avis"));
    }

    private static boolean startsWith(byte[] data, int offset, byte[] prefix) {
        if (data.length < offset + prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[offset + i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }
}
