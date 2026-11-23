package com.gongbang.post.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** PST-007 ②: 파일 앞부분 바이트로 JPG·PNG·WebP·GIF·AVIF를 가려내는지. */
class PostImageTypeTest {

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13};
    static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16};

    static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    /** ISO BMFF ftyp 상자: 크기(4) + "ftyp" + 주 브랜드(4) + 버전(4) + 호환 브랜드들. */
    static byte[] ftyp(String major, String... compatible) {
        int size = 16 + compatible.length * 4;
        byte[] box = new byte[size];
        box[3] = (byte) size;
        System.arraycopy(ascii("ftyp"), 0, box, 4, 4);
        System.arraycopy(ascii(major), 0, box, 8, 4);
        for (int i = 0; i < compatible.length; i++) {
            System.arraycopy(ascii(compatible[i]), 0, box, 16 + i * 4, 4);
        }
        return box;
    }

    @Test
    @DisplayName("JPG·PNG·GIF(87a·89a)를 알아본다")
    void 기본_형식() {
        assertThat(PostImageType.detect(JPG)).contains(PostImageType.JPG);
        assertThat(PostImageType.detect(PNG)).contains(PostImageType.PNG);
        assertThat(PostImageType.detect(ascii("GIF87a....."))).contains(PostImageType.GIF);
        assertThat(PostImageType.detect(ascii("GIF89a....."))).contains(PostImageType.GIF);
    }

    @Test
    @DisplayName("WebP는 RIFF 뒤 8바이트 위치에 WEBP가 있어야 한다 (WAV 같은 다른 RIFF는 아님)")
    void webp() {
        assertThat(PostImageType.detect(ascii("RIFF\u0000\u0000\u0000\u0000WEBPVP8 ")))
                .contains(PostImageType.WEBP);
        assertThat(PostImageType.detect(ascii("RIFF\u0000\u0000\u0000\u0000WAVEfmt "))).isEmpty();
    }

    @Test
    @DisplayName("AVIF는 ftyp의 주 브랜드나 호환 브랜드가 avif·avis면 알아보고, MP4(isom)·HEIC(heic)는 아니다")
    void avif() {
        assertThat(PostImageType.detect(ftyp("avif", "mif1", "miaf"))).contains(PostImageType.AVIF);
        assertThat(PostImageType.detect(ftyp("mif1", "avif", "miaf"))).contains(PostImageType.AVIF);
        assertThat(PostImageType.detect(ftyp("avis", "msf1"))).contains(PostImageType.AVIF);
        assertThat(PostImageType.detect(ftyp("isom", "iso2", "mp41"))).isEmpty();
        assertThat(PostImageType.detect(ftyp("heic", "mif1"))).isEmpty();
    }

    @Test
    @DisplayName("SVG·PDF·텍스트·빈 파일·너무 짧은 파일은 올릴 수 없다")
    void 지원하지_않는_형식() {
        assertThat(PostImageType.detect(ascii("<svg xmlns=\"http://www.w3.org/2000/svg\">")))
                .isEmpty();
        assertThat(PostImageType.detect(ascii("%PDF-1.7"))).isEmpty();
        assertThat(PostImageType.detect(ascii("hello"))).isEmpty();
        assertThat(PostImageType.detect(new byte[0])).isEmpty();
        assertThat(PostImageType.detect(new byte[] {(byte) 0xFF, (byte) 0xD8})).isEmpty();
        assertThat(PostImageType.detect(null)).isEmpty();
    }
}
