package com.gongbang.board.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.gongbang.board.domain.BoardCategory;
import com.gongbang.board.domain.BoardPost;
import com.gongbang.board.domain.BoardPostImage;
import com.gongbang.board.dto.BoardPostDetailResponse.ImageInfo;
import com.gongbang.board.repository.BoardPostImageRepository;
import com.gongbang.board.repository.BoardPostRepository;
import com.gongbang.file.service.AllowedImageType;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.post.service.ImageFolder;
import com.gongbang.post.service.PostImageUploader;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

/** BRD-006 결과물 사진 규칙 (권한, 결과물 자랑만, 형식·크기, 3장 제한, 소프트 삭제, 순서, 말머리 변경 때 모두 삭제). */
@ExtendWith(MockitoExtension.class)
class BoardPostImageServiceTest {

    private static final Long POST_ID = 10L;
    private static final Long AUTHOR_ID = 1L;
    private static final Long OTHER_ID = 2L;
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0};

    @Mock private BoardPostRepository boardPostRepository;
    @Mock private BoardPostImageRepository boardPostImageRepository;
    @Mock private PostImageUploader uploader;

    private BoardPostImageService service;

    @BeforeEach
    void setUp() {
        service =
                new BoardPostImageService(boardPostRepository, boardPostImageRepository, uploader);
    }

    private void givenPost(BoardCategory category) {
        BoardPost post = BoardPost.of(AUTHOR_ID, category, null, "제목", "내용", null);
        ReflectionTestUtils.setField(post, "id", POST_ID);
        given(boardPostRepository.findByIdForUpdate(POST_ID)).willReturn(Optional.of(post));
    }

    private void givenImages(BoardPostImage... images) {
        given(
                        boardPostImageRepository
                                .findByBoardPostIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(
                                        POST_ID))
                .willReturn(new ArrayList<>(List.of(images)));
    }

    private static BoardPostImage image(long id, int sortOrder) {
        BoardPostImage image =
                BoardPostImage.of(POST_ID, "/uploads/board/" + id + ".png", sortOrder);
        ReflectionTestUtils.setField(image, "id", id);
        return image;
    }

    private static MockMultipartFile png(int size) {
        byte[] bytes = new byte[size];
        System.arraycopy(PNG, 0, bytes, 0, PNG.length);
        return new MockMultipartFile("file", "work.png", "image/png", bytes);
    }

    private static BusinessException fails(Runnable action) {
        BusinessException e = catchThrowableOfType(action::run, BusinessException.class);
        assertThat(e).isNotNull();
        return e;
    }

    @Test
    @DisplayName("결과물 자랑 글의 작성자가 올리면 board 폴더에 저장하고 맨 뒤에 붙인다")
    void 올리기() throws Exception {
        givenPost(BoardCategory.SHOWCASE);
        givenImages(image(1, 0));
        MockMultipartFile file = png(100);
        given(uploader.store(ImageFolder.BOARD, POST_ID, AllowedImageType.PNG, file))
                .willReturn("/uploads/board/new.png");
        given(boardPostImageRepository.save(any()))
                .willAnswer(
                        invocation -> {
                            BoardPostImage saved = invocation.getArgument(0);
                            ReflectionTestUtils.setField(saved, "id", 2L);
                            return saved;
                        });

        List<ImageInfo> images = service.upload(AUTHOR_ID, POST_ID, file).images();

        assertThat(images)
                .containsExactly(
                        new ImageInfo(1L, "/uploads/board/1.png"),
                        new ImageInfo(2L, "/uploads/board/new.png"));
    }

    @Test
    @DisplayName("결과물 자랑이 아닌 글에는 올릴 수 없다: 400과 BRD-006 ② 문구")
    void 결과물_자랑만() {
        givenPost(BoardCategory.TIP);

        BusinessException e = fails(() -> service.upload(AUTHOR_ID, POST_ID, png(100)));
        assertThat(e.getMessage()).isEqualTo(BoardPostImageService.SHOWCASE_ONLY_MESSAGE);
        verifyNoInteractions(uploader);
    }

    @Test
    @DisplayName("작성자가 아니면 403, 10MB 초과·사진이 아닌 파일은 400, 3장이면 409")
    void 올리기_검사() {
        givenPost(BoardCategory.SHOWCASE);
        assertThat(fails(() -> service.upload(OTHER_ID, POST_ID, png(100))).getErrorCode())
                .isEqualTo(ErrorCode.NOT_BOARD_POST_AUTHOR);
        assertThat(
                        fails(
                                        () ->
                                                service.upload(
                                                        AUTHOR_ID,
                                                        POST_ID,
                                                        png(
                                                                (int)
                                                                                BoardPostImageService
                                                                                        .MAX_BYTES
                                                                        + 1)))
                                .getMessage())
                .isEqualTo(BoardPostImageService.SIZE_MESSAGE);
        MockMultipartFile svg =
                new MockMultipartFile("file", "a.png", "image/png", "<svg/>".getBytes());
        assertThat(fails(() -> service.upload(AUTHOR_ID, POST_ID, svg)).getMessage())
                .isEqualTo(BoardPostImageService.TYPE_MESSAGE);

        givenImages(image(1, 0), image(2, 1), image(3, 2));
        assertThat(fails(() -> service.upload(AUTHOR_ID, POST_ID, png(100))).getErrorCode())
                .isEqualTo(ErrorCode.BOARD_POST_IMAGE_LIMIT);
        verifyNoInteractions(uploader);
    }

    @Test
    @DisplayName("삭제는 소프트 삭제이고 남은 순서를 0부터 다시 매긴다, 없는 사진이면 404")
    void 삭제() throws Exception {
        givenPost(BoardCategory.SHOWCASE);
        BoardPostImage first = image(1, 0);
        BoardPostImage second = image(2, 1);
        givenImages(first, second);

        assertThat(service.delete(AUTHOR_ID, POST_ID, 1L).images())
                .extracting(ImageInfo::imageId)
                .containsExactly(2L);
        assertThat(first.getDeletedAt()).isNotNull();
        assertThat(second.getSortOrder()).isZero();
        verifyNoInteractions(uploader);

        givenImages(second);
        assertThat(fails(() -> service.delete(AUTHOR_ID, POST_ID, 99L)).getErrorCode())
                .isEqualTo(ErrorCode.BOARD_POST_IMAGE_NOT_FOUND);
    }

    @Test
    @DisplayName("순서 변경: 받은 순서로 매기고, 빠지거나 겹치면 400")
    void 순서_변경() {
        givenPost(BoardCategory.SHOWCASE);
        BoardPostImage first = image(1, 0);
        BoardPostImage second = image(2, 1);
        givenImages(first, second);

        service.reorder(AUTHOR_ID, POST_ID, List.of(2L, 1L));
        assertThat(second.getSortOrder()).isZero();
        assertThat(first.getSortOrder()).isEqualTo(1);

        for (List<Long> wrong : List.of(List.of(1L), List.of(1L, 1L), List.of(1L, 9L))) {
            assertThat(fails(() -> service.reorder(AUTHOR_ID, POST_ID, wrong)).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_INPUT);
        }
    }

    @Test
    @DisplayName("말머리를 바꿀 때 남은 사진을 모두 소프트 삭제한다 (BRD-003 ③)")
    void 모두_삭제() {
        BoardPostImage first = image(1, 0);
        BoardPostImage second = image(2, 1);
        givenImages(first, second);
        LocalDateTime now = LocalDateTime.of(2026, 11, 23, 12, 0);

        service.deleteAll(POST_ID, now);

        assertThat(first.getDeletedAt()).isEqualTo(now);
        assertThat(second.getDeletedAt()).isEqualTo(now);
    }
}
