package com.gongbang.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gongbang.file.service.AllowedImageType;
import com.gongbang.file.service.FileStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** PST-007·BRD-006 사진을 공용 저장소에 두는 규칙: 키 모양, 주소 길이, 되돌릴 때 파일 정리. */
@ExtendWith(MockitoExtension.class)
class PostImageUploaderTest {

    @Mock private FileStorage fileStorage;

    private PostImageUploader uploader;
    private final MockMultipartFile file =
            new MockMultipartFile("file", "a.png", "image/png", new byte[] {1, 2, 3});

    @BeforeEach
    void setUp() {
        uploader = new PostImageUploader(fileStorage);
        given(fileStorage.resolveUrl(anyString()))
                .willAnswer(invocation -> "https://cdn.example.com/" + invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("{폴더}/{글 ID}/{uuid}.{확장자} 키로 형식에 맞는 Content-Type과 함께 저장하고 공개 주소를 돌려준다")
    void 저장_키와_주소() throws Exception {
        String url = uploader.store(ImageFolder.BOARD, 7L, AllowedImageType.WEBP, file);

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(fileStorage).store(key.capture(), eq("image/webp"), eq(file));
        assertThat(key.getValue()).matches("board/7/[0-9a-f-]{36}\\.webp");
        assertThat(url).isEqualTo("https://cdn.example.com/" + key.getValue());
    }

    @Test
    @DisplayName("주소가 image_url 칸(500자)보다 길면 저장하지 않고 실패한다")
    void 주소_길이() throws Exception {
        given(fileStorage.resolveUrl(anyString())).willReturn("x".repeat(501));

        assertThatThrownBy(() -> uploader.store(ImageFolder.POST, 1L, AllowedImageType.PNG, file))
                .isInstanceOf(IllegalStateException.class);
        verify(fileStorage, never()).store(any(), any(), any());
    }

    @Test
    @DisplayName("트랜잭션이 되돌려지면 방금 저장한 파일을 지우고, 커밋되면 남긴다")
    void 되돌리면_파일_정리() throws Exception {
        TransactionSynchronizationManager.initSynchronization();
        uploader.store(ImageFolder.POST, 1L, AllowedImageType.PNG, file);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(fileStorage).store(key.capture(), any(), any());

        for (TransactionSynchronization sync :
                TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        }
        verify(fileStorage, never()).deleteQuietly(any());

        for (TransactionSynchronization sync :
                TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }
        verify(fileStorage).deleteQuietly(key.getValue());
    }
}
