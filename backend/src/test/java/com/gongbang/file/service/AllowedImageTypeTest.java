package com.gongbang.file.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AllowedImageTypeTest {

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13};
    static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16};

    static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }

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
    @DisplayName("JPG·PNG·GIF 매직 바이트를 정확히 판별한다")
    void 기본_형식() {
        assertThat(AllowedImageType.detect(JPG)).contains(AllowedImageType.JPG);
        assertThat(AllowedImageType.detect(PNG)).contains(AllowedImageType.PNG);
        assertThat(AllowedImageType.detect(ascii("GIF87a....."))).contains(AllowedImageType.GIF);
        assertThat(AllowedImageType.detect(ascii("GIF89a....."))).contains(AllowedImageType.GIF);
    }

    @Test
    @DisplayName("WebP는 RIFF+WEBP 헤더만 허용한다")
    void webp() {
        assertThat(AllowedImageType.detect(ascii("RIFF\u0000\u0000\u0000\u0000WEBPVP8 ")))
                .contains(AllowedImageType.WEBP);
        assertThat(AllowedImageType.detect(ascii("RIFF\u0000\u0000\u0000\u0000WAVEfmt ")))
                .isEmpty();
    }

    @Test
    @DisplayName("AVIF는 ftyp 주·호환 브랜드가 avif/avis인 경우만 허용한다")
    void avif() {
        assertThat(AllowedImageType.detect(ftyp("avif", "mif1"))).contains(AllowedImageType.AVIF);
        assertThat(AllowedImageType.detect(ftyp("mif1", "avif"))).contains(AllowedImageType.AVIF);
        assertThat(AllowedImageType.detect(ftyp("avis", "msf1"))).contains(AllowedImageType.AVIF);
        assertThat(AllowedImageType.detect(ftyp("isom", "iso2", "mp41"))).isEmpty();
        assertThat(AllowedImageType.detect(ftyp("heic", "mif1"))).isEmpty();
    }

    @Test
    @DisplayName("SVG·PDF·빈 바이트·짧은 헤더는 거부하고 InputStream 오버로드도 동일하게 동작한다")
    void 지원하지_않는_형식() throws Exception {
        assertThat(AllowedImageType.detect(ascii("<svg xmlns=\"http://www.w3.org/2000/svg\">")))
                .isEmpty();
        assertThat(AllowedImageType.detect(ascii("%PDF-1.7"))).isEmpty();
        assertThat(AllowedImageType.detect(new byte[0])).isEmpty();
        assertThat(AllowedImageType.detect(new byte[] {(byte) 0xFF, (byte) 0xD8})).isEmpty();
        assertThat(AllowedImageType.detect((byte[]) null)).isEmpty();
        assertThat(AllowedImageType.detect((ByteArrayInputStream) null)).isEmpty();
        assertThat(AllowedImageType.detect(new ByteArrayInputStream(PNG)))
                .contains(AllowedImageType.PNG);
    }
}
