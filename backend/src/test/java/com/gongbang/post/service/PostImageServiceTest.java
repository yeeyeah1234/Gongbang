package com.gongbang.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.gongbang.file.service.AllowedImageType;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.post.domain.ExternalContest;
import com.gongbang.post.domain.RecruitPost;
import com.gongbang.post.domain.RecruitPostImage;
import com.gongbang.post.dto.PostImageResponse;
import com.gongbang.post.dto.PostImagesResponse;
import com.gongbang.post.repository.RecruitPostImageRepository;
import com.gongbang.post.repository.RecruitPostRepository;
import java.time.LocalDate;
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

/** PST-007 사진 올리기·삭제·순서 변경의 규칙 (권한, 외부 공모전만, 형식·크기, 3장 제한, 소프트 삭제, 순서). */
@ExtendWith(MockitoExtension.class)
class PostImageServiceTest {

    private static final Long POST_ID = 10L;
    private static final byte[] PNG = {
        (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13
    };
    private static final Long AUTHOR_ID = 1L;
    private static final Long OTHER_ID = 2L;
    private static final LocalDate DEADLINE = LocalDate.of(2099, 12, 31);

    @Mock private RecruitPostRepository recruitPostRepository;
    @Mock private RecruitPostImageRepository recruitPostImageRepository;
    @Mock private PostImageUploader uploader;

    private PostImageService service;

    @BeforeEach
    void setUp() {
        service = new PostImageService(recruitPostRepository, recruitPostImageRepository, uploader);
    }

    private static RecruitPost externalPost() {
        RecruitPost post =
                RecruitPost.withExternalContest(
                        AUTHOR_ID,
                        ExternalContest.of("외부 공모전", 5L, DEADLINE, null),
                        "모집글",
                        "내용",
                        3,
                        LocalDate.of(2026, 11, 1),
                        DEADLINE,
                        null);
        ReflectionTestUtils.setField(post, "id", POST_ID);
        return post;
    }

    private static RecruitPostImage image(long id, int sortOrder) {
        RecruitPostImage image =
                RecruitPostImage.of(POST_ID, "/uploads/post/" + id + ".png", sortOrder);
        ReflectionTestUtils.setField(image, "id", id);
        return image;
    }

    private static MockMultipartFile png(int size) {
        byte[] bytes = new byte[size];
        System.arraycopy(PNG, 0, bytes, 0, PNG.length);
        return new MockMultipartFile("file", "poster.png", "image/png", bytes);
    }

    private void givenPost(RecruitPost post) {
        given(recruitPostRepository.findByIdForUpdate(POST_ID)).willReturn(Optional.of(post));
    }

    private void givenImages(RecruitPostImage... images) {
        given(
                        recruitPostImageRepository
                                .findByPostIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(POST_ID))
                .willReturn(new ArrayList<>(List.of(images)));
    }

    private static BusinessException fails(Runnable action) {
        BusinessException e = catchThrowableOfType(action::run, BusinessException.class);
        assertThat(e).isNotNull();
        return e;
    }

    @Test
    @DisplayName("작성자가 올리면 맨 뒤(sortOrder = 지금 장수)에 붙고 전체 목록을 돌려준다")
    void 올리기_성공() throws Exception {
        givenPost(externalPost());
        givenImages(image(1, 0));
        MockMultipartFile file = png(100);
        given(uploader.store(ImageFolder.POST, POST_ID, AllowedImageType.PNG, file))
                .willReturn("/uploads/post/new.png");
        given(recruitPostImageRepository.save(any()))
                .willAnswer(
                        invocation -> {
                            RecruitPostImage saved = invocation.getArgument(0);
                            ReflectionTestUtils.setField(saved, "id", 2L);
                            return saved;
                        });

        PostImagesResponse response = service.upload(AUTHOR_ID, POST_ID, file);

        assertThat(response.images())
                .containsExactly(
                        new PostImageResponse(1L, "/uploads/post/1.png"),
                        new PostImageResponse(2L, "/uploads/post/new.png"));
        verify(recruitPostImageRepository)
                .save(
                        org.mockito.ArgumentMatchers.argThat(
                                saved ->
                                        saved.getSortOrder() == 1
                                                && saved.getPostId().equals(POST_ID)));
    }

    @Test
    @DisplayName("작성자가 아니면 403이고 파일을 저장하지 않는다")
    void 작성자가_아니면_403() {
        givenPost(externalPost());

        assertThat(fails(() -> service.upload(OTHER_ID, POST_ID, png(100))).getErrorCode())
                .isEqualTo(ErrorCode.NOT_POST_AUTHOR);
        verifyNoInteractions(uploader);
    }

    @Test
    @DisplayName("없거나 삭제된 글이면 404")
    void 삭제된_글이면_404() {
        RecruitPost post = externalPost();
        ReflectionTestUtils.setField(post, "deletedAt", LocalDateTime.of(2026, 11, 20, 0, 0));
        givenPost(post);

        assertThat(fails(() -> service.upload(AUTHOR_ID, POST_ID, png(100))).getErrorCode())
                .isEqualTo(ErrorCode.POST_NOT_FOUND);
        given(recruitPostRepository.findByIdForUpdate(99L)).willReturn(Optional.empty());
        assertThat(fails(() -> service.delete(AUTHOR_ID, 99L, 1L)).getErrorCode())
                .isEqualTo(ErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("서비스 공모전 글에는 올릴 수 없다 (①)")
    void 서비스_공모전_글은_400() {
        RecruitPost post =
                RecruitPost.withContest(
                        AUTHOR_ID, 3L, "모집글", "내용", 3, LocalDate.of(2026, 11, 1), DEADLINE, null);
        givenPost(post);

        BusinessException e = fails(() -> service.upload(AUTHOR_ID, POST_ID, png(100)));
        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
        assertThat(e.getMessage()).isEqualTo(PostImageService.EXTERNAL_ONLY_MESSAGE);
        verifyNoInteractions(uploader);
    }

    @Test
    @DisplayName("10MB를 넘으면 400 (②), 딱 10MB는 올릴 수 있다")
    void 크기_제한() throws Exception {
        givenPost(externalPost());

        BusinessException e =
                fails(
                        () ->
                                service.upload(
                                        AUTHOR_ID,
                                        POST_ID,
                                        png((int) PostImageService.MAX_BYTES + 1)));
        assertThat(e.getMessage()).isEqualTo(PostImageService.SIZE_MESSAGE);
        verifyNoInteractions(uploader);

        givenImages();
        MockMultipartFile exact = png((int) PostImageService.MAX_BYTES);
        given(uploader.store(ImageFolder.POST, POST_ID, AllowedImageType.PNG, exact))
                .willReturn("/uploads/post/a.png");
        given(recruitPostImageRepository.save(any()))
                .willAnswer(invocation -> invocation.getArgument(0));
        assertThat(service.upload(AUTHOR_ID, POST_ID, exact).images()).hasSize(1);
    }

    @Test
    @DisplayName("이름이 .png여도 내용이 사진이 아니면 400 (②)")
    void 형식_검사() {
        givenPost(externalPost());
        MockMultipartFile fake =
                new MockMultipartFile("file", "poster.png", "image/png", "<svg></svg>".getBytes());

        BusinessException e = fails(() -> service.upload(AUTHOR_ID, POST_ID, fake));
        assertThat(e.getMessage()).isEqualTo(PostImageService.TYPE_MESSAGE);
        verifyNoInteractions(uploader);
    }

    @Test
    @DisplayName("파일이 없거나 비어 있으면 400")
    void 빈_파일() {
        givenPost(externalPost());

        assertThat(fails(() -> service.upload(AUTHOR_ID, POST_ID, null)).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
        assertThat(
                        fails(
                                        () ->
                                                service.upload(
                                                        AUTHOR_ID,
                                                        POST_ID,
                                                        new MockMultipartFile("file", new byte[0])))
                                .getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("이미 3장이면 409 POST_IMAGE_LIMIT이고 파일을 저장하지 않는다 (②)")
    void 세_장이면_409() {
        givenPost(externalPost());
        givenImages(image(1, 0), image(2, 1), image(3, 2));

        assertThat(fails(() -> service.upload(AUTHOR_ID, POST_ID, png(100))).getErrorCode())
                .isEqualTo(ErrorCode.POST_IMAGE_LIMIT);
        verifyNoInteractions(uploader);
    }

    @Test
    @DisplayName("저장소가 실패하면 500과 안내 문구")
    void 저장_실패() throws Exception {
        givenPost(externalPost());
        givenImages();
        MockMultipartFile file = png(100);
        given(uploader.store(ImageFolder.POST, POST_ID, AllowedImageType.PNG, file))
                .willThrow(new java.io.IOException("disk"));

        BusinessException e = fails(() -> service.upload(AUTHOR_ID, POST_ID, file));
        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);
        assertThat(e.getMessage()).isEqualTo(PostImageService.STORE_FAILED_MESSAGE);
        verify(recruitPostImageRepository, never()).save(any());
    }

    @Test
    @DisplayName("삭제는 소프트 삭제이고 파일은 지우지 않으며, 남은 사진 순서를 0부터 다시 매긴다 (④)")
    void 삭제() throws Exception {
        givenPost(externalPost());
        RecruitPostImage first = image(1, 0);
        RecruitPostImage second = image(2, 1);
        RecruitPostImage third = image(3, 2);
        givenImages(first, second, third);

        PostImagesResponse response = service.delete(AUTHOR_ID, POST_ID, 1L);

        assertThat(first.getDeletedAt()).isNotNull();
        assertThat(second.getSortOrder()).isZero();
        assertThat(third.getSortOrder()).isEqualTo(1);
        assertThat(response.images())
                .extracting(PostImageResponse::imageId)
                .containsExactly(2L, 3L);
        verifyNoInteractions(uploader);
    }

    @Test
    @DisplayName("이 글에 없거나 이미 지운 사진이면 404, 작성자가 아니면 403")
    void 삭제_실패() {
        givenPost(externalPost());
        givenImages(image(1, 0));

        assertThat(fails(() -> service.delete(AUTHOR_ID, POST_ID, 99L)).getErrorCode())
                .isEqualTo(ErrorCode.POST_IMAGE_NOT_FOUND);
        assertThat(fails(() -> service.delete(OTHER_ID, POST_ID, 1L)).getErrorCode())
                .isEqualTo(ErrorCode.NOT_POST_AUTHOR);
    }

    @Test
    @DisplayName("순서 변경: 받은 순서대로 0부터 매기고 첫 번째가 대표가 된다 (③)")
    void 순서_변경() {
        givenPost(externalPost());
        RecruitPostImage first = image(1, 0);
        RecruitPostImage second = image(2, 1);
        RecruitPostImage third = image(3, 2);
        givenImages(first, second, third);

        PostImagesResponse response = service.reorder(AUTHOR_ID, POST_ID, List.of(3L, 1L, 2L));

        assertThat(third.getSortOrder()).isZero();
        assertThat(first.getSortOrder()).isEqualTo(1);
        assertThat(second.getSortOrder()).isEqualTo(2);
        assertThat(response.images())
                .extracting(PostImageResponse::imageId)
                .containsExactly(3L, 1L, 2L);
    }

    @Test
    @DisplayName("순서 변경: 빠지거나 겹치거나 다른 사진이 섞이거나 비어 있으면 400")
    void 순서_변경_검사() {
        givenPost(externalPost());
        givenImages(image(1, 0), image(2, 1));

        for (List<Long> wrong :
                List.of(List.of(1L), List.of(1L, 1L), List.of(1L, 9L), List.of(1L, 2L, 3L))) {
            assertThat(fails(() -> service.reorder(AUTHOR_ID, POST_ID, wrong)).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_INPUT);
        }
        assertThat(fails(() -> service.reorder(AUTHOR_ID, POST_ID, null)).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("상세용 목록은 저장소 순서 그대로 돌려준다")
    void 상세용_목록() {
        givenImages(image(2, 0), image(1, 1));

        assertThat(service.findImages(POST_ID))
                .extracting(PostImageResponse::imageId)
                .containsExactly(2L, 1L);
        verify(recruitPostRepository, never()).findByIdForUpdate(anyLong());
    }
}
