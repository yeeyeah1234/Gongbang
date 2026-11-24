package com.gongbang.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gongbang.file.domain.FilePurpose;
import com.gongbang.file.domain.UploadedFile;
import com.gongbang.file.dto.FileUploadResponse;
import com.gongbang.file.repository.UploadedFileRepository;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FileUploadServiceTest {

    @Mock private UploadedFileRepository uploadedFileRepository;
    @Mock private FileStorage fileStorage;

    private FileUploadService service;

    @BeforeEach
    void setUp() {
        service = new FileUploadService(uploadedFileRepository, fileStorage);
    }

    @Test
    @DisplayName("CHAT 이미지 업로드 시 매직 바이트 검증 후 저장하고 fileId와 URL을 반환한다")
    void 채팅_이미지_업로드_성공() throws Exception {
        MockMultipartFile file =
                new MockMultipartFile(
                        "file", "../../screenshot.png", "image/png", AllowedImageTypeTest.PNG);
        doNothing().when(fileStorage).store(any(), eq("image/png"), eq(file));
        given(uploadedFileRepository.save(any(UploadedFile.class)))
                .willAnswer(
                        inv -> {
                            UploadedFile entity = inv.getArgument(0);
                            ReflectionTestUtils.setField(entity, "id", 101L);
                            return entity;
                        });
        given(fileStorage.resolveUrl(any())).willAnswer(inv -> "/uploads/" + inv.getArgument(0));

        FileUploadResponse response = service.upload(7L, "chat", file);

        ArgumentCaptor<UploadedFile> savedCaptor = ArgumentCaptor.forClass(UploadedFile.class);
        verify(uploadedFileRepository).save(savedCaptor.capture());
        UploadedFile saved = savedCaptor.getValue();
        assertThat(saved.getUploaderId()).isEqualTo(7L);
        assertThat(saved.getPurpose()).isEqualTo(FilePurpose.CHAT);
        assertThat(saved.getStorageKey()).startsWith("chat/7/").endsWith(".png");
        assertThat(saved.getOriginalName()).isEqualTo("screenshot.png");
        assertThat(saved.getContentType()).isEqualTo("image/png");
        assertThat(saved.getSizeBytes()).isEqualTo(AllowedImageTypeTest.PNG.length);
        assertThat(response.fileId()).isEqualTo(101L);
        assertThat(response.url()).isEqualTo("/uploads/" + saved.getStorageKey());
    }

    @Test
    @DisplayName("5MB를 초과하는 파일은 FILE_TOO_LARGE를 반환한다")
    void 용량_초과_거부() throws Exception {
        byte[] oversize = new byte[(int) FileUploadService.MAX_FILE_SIZE_BYTES + 1];
        System.arraycopy(AllowedImageTypeTest.PNG, 0, oversize, 0, AllowedImageTypeTest.PNG.length);
        MockMultipartFile file = new MockMultipartFile("file", "big.png", "image/png", oversize);

        assertThatThrownBy(() -> service.upload(7L, "CHAT", file))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FILE_TOO_LARGE);
        verify(fileStorage, never()).store(any(), any(), any());
    }

    @Test
    @DisplayName("Content-Type이 image/png여도 실제 내용이 SVG·문서면 UNSUPPORTED_FILE_TYPE을 반환한다")
    void 위장_파일_거부() throws Exception {
        MockMultipartFile fakePng =
                new MockMultipartFile(
                        "file",
                        "xss.png",
                        "image/png",
                        "<svg onload=alert(1)>".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.upload(7L, "CHAT", fakePng))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNSUPPORTED_FILE_TYPE);
        verify(fileStorage, never()).store(any(), any(), any());
    }

    @Test
    @DisplayName("비공개 등 미지원 용도(PROOF 등)나 빈 파일은 INVALID_INPUT을 반환한다")
    void 미지원_용도_및_빈파일_거부() {
        MockMultipartFile png =
                new MockMultipartFile("file", "a.png", "image/png", AllowedImageTypeTest.PNG);
        MockMultipartFile empty =
                new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> service.upload(7L, "PROOF", png))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);

        assertThatThrownBy(() -> service.upload(7L, "UNKNOWN", png))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);

        assertThatThrownBy(() -> service.upload(7L, "CHAT", empty))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("DB 저장 실패 시 이미 업로드한 저장소 객체를 정리한다")
    void DB_실패시_저장객체_정리() throws Exception {
        MockMultipartFile file =
                new MockMultipartFile("file", "a.png", "image/png", AllowedImageTypeTest.PNG);
        given(uploadedFileRepository.save(any(UploadedFile.class)))
                .willThrow(new RuntimeException("db failure"));

        assertThatThrownBy(() -> service.upload(7L, "CHAT", file))
                .isInstanceOf(RuntimeException.class);
        verify(fileStorage).deleteQuietly(any());
    }

    @Test
    @DisplayName("requireOwnedChatFile은 본인 CHAT 업로드만 통과시키고 타인·타용도·미존재는 예외를 던진다")
    void 채팅_파일_소유권_용도_검증() {
        UploadedFile ownChat =
                UploadedFile.create(
                        7L, FilePurpose.CHAT, "chat/7/a.png", "a.png", "image/png", 10L);
        UploadedFile otherOwner =
                UploadedFile.create(
                        8L, FilePurpose.CHAT, "chat/8/b.png", "b.png", "image/png", 10L);
        UploadedFile wrongPurpose =
                UploadedFile.create(
                        7L, FilePurpose.PROFILE, "profile/7/c.png", "c.png", "image/png", 10L);

        given(uploadedFileRepository.findById(1L)).willReturn(Optional.of(ownChat));
        given(uploadedFileRepository.findById(2L)).willReturn(Optional.of(otherOwner));
        given(uploadedFileRepository.findById(3L)).willReturn(Optional.of(wrongPurpose));
        given(uploadedFileRepository.findById(99L)).willReturn(Optional.empty());

        assertThatCode(() -> service.requireOwnedChatFile(1L, 7L)).doesNotThrowAnyException();

        assertThatThrownBy(() -> service.requireOwnedChatFile(2L, 7L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_OWNER);

        assertThatThrownBy(() -> service.requireOwnedChatFile(3L, 7L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);

        assertThatThrownBy(() -> service.requireOwnedChatFile(99L, 7L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("getFileUrls는 파일 ID별 접근 URL 맵을 반환한다")
    void 파일_URL_일괄_조회() {
        UploadedFile f1 =
                UploadedFile.create(
                        7L, FilePurpose.CHAT, "chat/7/1.png", "1.png", "image/png", 10L);
        ReflectionTestUtils.setField(f1, "id", 10L);
        given(uploadedFileRepository.findAllById(List.of(10L))).willReturn(List.of(f1));
        given(fileStorage.resolveUrl("chat/7/1.png")).willReturn("/uploads/chat/7/1.png");

        Map<Long, String> urls = service.getFileUrls(List.of(10L));

        assertThat(urls).containsExactlyEntriesOf(Map.of(10L, "/uploads/chat/7/1.png"));
        assertThat(service.getFileUrls(List.of())).isEmpty();
    }

    @Test
    void 비공개_증빙은_공개_URL을_생성하지_않는다() {
        UploadedFile proof =
                UploadedFile.create(
                        7L,
                        FilePurpose.PROOF,
                        "local:proof/test",
                        "proof.pdf",
                        "application/pdf",
                        10L);
        ReflectionTestUtils.setField(proof, "id", 11L);
        given(uploadedFileRepository.findAllById(List.of(11L))).willReturn(List.of(proof));
        assertThat(service.getFileUrls(List.of(11L))).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(fileStorage);
    }
}
