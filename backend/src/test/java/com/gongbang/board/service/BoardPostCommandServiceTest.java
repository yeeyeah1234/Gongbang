package com.gongbang.board.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gongbang.board.domain.BoardCategory;
import com.gongbang.board.domain.BoardPost;
import com.gongbang.board.dto.BoardPostLikeResponse;
import com.gongbang.board.dto.BoardPostRequest;
import com.gongbang.board.repository.BoardPostRepository;
import com.gongbang.contest.dto.ContestSummary;
import com.gongbang.contest.service.ContestLookupService;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** BRD-002 작성 검사, BRD-003 수정·삭제 권한, BRD-007 좋아요 규칙. */
@ExtendWith(MockitoExtension.class)
class BoardPostCommandServiceTest {

    private static final Long AUTHOR_ID = 1L;
    private static final Long OTHER_ID = 2L;
    private static final Long POST_ID = 10L;
    private static final Long CONTEST_ID = 3L;

    @Mock private BoardPostRepository boardPostRepository;
    @Mock private ContestLookupService contestLookupService;
    @Mock private BoardPostQueryService boardPostQueryService;
    @Mock private BoardPostImageService boardPostImageService;

    @Mock
    private com.gongbang.notification.service.CommunityNotificationService communityNotifications;

    private BoardPostCommandService service;

    @BeforeEach
    void setUp() {
        service =
                new BoardPostCommandService(
                        boardPostRepository,
                        contestLookupService,
                        boardPostQueryService,
                        boardPostImageService,
                        communityNotifications);
    }

    private static BoardPostRequest request(
            BoardCategory category, Long contestId, String linkUrl) {
        return new BoardPostRequest(category, contestId, " 제목 ", " 내용 ", linkUrl);
    }

    private void givenContest() {
        given(contestLookupService.findSummary(CONTEST_ID))
                .willReturn(
                        Optional.of(
                                new ContestSummary(
                                        CONTEST_ID, "공모전", "IT", LocalDate.of(2020, 1, 1), null)));
    }

    private static BoardPost post(BoardCategory category, String linkUrl) {
        BoardPost post = BoardPost.of(AUTHOR_ID, category, null, "제목", "내용", linkUrl);
        ReflectionTestUtils.setField(post, "id", POST_ID);
        return post;
    }

    private void givenLocked(BoardPost post) {
        given(boardPostRepository.findByIdForUpdate(POST_ID)).willReturn(Optional.of(post));
    }

    private static BusinessException fails(Runnable action) {
        BusinessException e = catchThrowableOfType(action::run, BusinessException.class);
        assertThat(e).isNotNull();
        return e;
    }

    private BoardPost saved() {
        ArgumentCaptor<BoardPost> captor = ArgumentCaptor.forClass(BoardPost.class);
        verify(boardPostRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("공모전 이야기는 공모전과 함께 쓴다. 마감된 공모전도 고를 수 있고 제목·내용은 앞뒤 공백을 뺀다 (BRD-002 ①)")
    void 공모전_이야기_작성() {
        givenContest();
        given(boardPostRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        service.create(AUTHOR_ID, request(BoardCategory.CONTEST, CONTEST_ID, null));

        BoardPost post = saved();
        assertThat(post.getAuthorId()).isEqualTo(AUTHOR_ID);
        assertThat(post.getContestId()).isEqualTo(CONTEST_ID);
        assertThat(post.getTitle()).isEqualTo("제목");
        verify(communityNotifications).postCreated(AUTHOR_ID, post.getId(), "제목");
        assertThat(post.getContent()).isEqualTo("내용");
    }

    @Test
    @DisplayName("공모전 이야기인데 공모전이 없으면 400 CONTEST_REQUIRED, 없는 공모전이면 404")
    void 공모전_검사() {
        assertThat(
                        fails(
                                        () ->
                                                service.create(
                                                        AUTHOR_ID,
                                                        request(BoardCategory.CONTEST, null, null)))
                                .getErrorCode())
                .isEqualTo(ErrorCode.CONTEST_REQUIRED);
        given(contestLookupService.findSummary(99L)).willReturn(Optional.empty());
        assertThat(
                        fails(
                                        () ->
                                                service.create(
                                                        AUTHOR_ID,
                                                        request(BoardCategory.TIP, 99L, null)))
                                .getErrorCode())
                .isEqualTo(ErrorCode.CONTEST_NOT_FOUND);
    }

    @Test
    @DisplayName("말머리·제목·내용이 없으면 400과 BRD-002 ④ 문구, 길이를 넘으면 400")
    void 필수값_검사() {
        assertThat(fails(() -> service.create(AUTHOR_ID, request(null, null, null))).getMessage())
                .isEqualTo(BoardPostCommandService.CATEGORY_REQUIRED);
        assertThat(
                        fails(
                                        () ->
                                                service.create(
                                                        AUTHOR_ID,
                                                        new BoardPostRequest(
                                                                BoardCategory.TIP,
                                                                null,
                                                                "  ",
                                                                "내용",
                                                                null)))
                                .getMessage())
                .isEqualTo(BoardPostCommandService.TITLE_REQUIRED);
        assertThat(
                        fails(
                                        () ->
                                                service.create(
                                                        AUTHOR_ID,
                                                        new BoardPostRequest(
                                                                BoardCategory.TIP,
                                                                null,
                                                                "제목",
                                                                null,
                                                                null)))
                                .getMessage())
                .isEqualTo(BoardPostCommandService.CONTENT_REQUIRED);
        assertThat(
                        fails(
                                        () ->
                                                service.create(
                                                        AUTHOR_ID,
                                                        new BoardPostRequest(
                                                                BoardCategory.TIP,
                                                                null,
                                                                "가".repeat(51),
                                                                "내용",
                                                                null)))
                                .getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
        verify(boardPostRepository, never()).save(any());
    }

    @Test
    @DisplayName("결과물 링크는 결과물 자랑에서만 저장하고 공통 URL 정책(대소문자 무관 http(s), 호스트 필수)만 받는다 (BRD-002 ②, #120)")
    void 결과물_링크() {
        given(boardPostRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        service.create(
                AUTHOR_ID, request(BoardCategory.SHOWCASE, null, " https://demo.example.com "));
        assertThat(saved().getLinkUrl()).isEqualTo("https://demo.example.com");
        clearInvocations(boardPostRepository);
        service.create(
                AUTHOR_ID, request(BoardCategory.SHOWCASE, null, "HTTPS://Demo.example.com/a"));
        assertThat(saved().getLinkUrl()).isEqualTo("HTTPS://Demo.example.com/a");

        for (String bad :
                new String[] {
                    "javascript:alert(1)",
                    "ftp://a",
                    "https://",
                    "https:///path",
                    "https://a b",
                    "https://" + "a".repeat(BoardPostCommandService.URL_MAX)
                }) {
            assertThat(
                            fails(
                                            () ->
                                                    service.create(
                                                            AUTHOR_ID,
                                                            request(
                                                                    BoardCategory.SHOWCASE,
                                                                    null,
                                                                    bad)))
                                    .getMessage())
                    .isEqualTo(BoardPostCommandService.URL_INVALID);
        }
    }

    @Test
    @DisplayName("결과물 자랑이 아닌 말머리면 링크를 보내도 저장하지 않는다")
    void 다른_말머리의_링크는_버린다() {
        given(boardPostRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        service.create(AUTHOR_ID, request(BoardCategory.TIP, null, "https://demo.example.com"));

        assertThat(saved().getLinkUrl()).isNull();
    }

    @Test
    @DisplayName("수정: 결과물 자랑에서 노하우로 바꾸면 결과물 링크가 지워진다 (BRD-003 ③)")
    void 말머리를_바꾸면_링크를_지운다() {
        BoardPost post = post(BoardCategory.SHOWCASE, "https://demo.example.com");
        givenLocked(post);

        service.update(
                AUTHOR_ID, POST_ID, request(BoardCategory.TIP, null, "https://demo.example.com"));

        assertThat(post.getCategory()).isEqualTo(BoardCategory.TIP);
        assertThat(post.getLinkUrl()).isNull();
        // 올린 사진도 소프트 삭제한다 (BRD-003 ③)
        verify(boardPostImageService).deleteAll(eq(POST_ID), any());
        verify(boardPostQueryService).describe(post, AUTHOR_ID);
    }

    @Test
    @DisplayName("수정: 결과물 자랑을 그대로 두거나 다른 말머리끼리 바꾸면 사진을 지우지 않는다")
    void 결과물_자랑을_유지하면_사진을_둔다() {
        givenLocked(post(BoardCategory.SHOWCASE, null));
        service.update(AUTHOR_ID, POST_ID, request(BoardCategory.SHOWCASE, null, null));

        givenLocked(post(BoardCategory.TIP, null));
        service.update(AUTHOR_ID, POST_ID, request(BoardCategory.SHOWCASE, null, null));

        verify(boardPostImageService, never()).deleteAll(anyLong(), any());
    }

    @Test
    @DisplayName("수정·삭제는 작성자만(403), 없거나 삭제된 글은 404")
    void 수정_삭제_권한() {
        givenLocked(post(BoardCategory.TIP, null));
        assertThat(
                        fails(
                                        () ->
                                                service.update(
                                                        OTHER_ID,
                                                        POST_ID,
                                                        request(BoardCategory.TIP, null, null)))
                                .getErrorCode())
                .isEqualTo(ErrorCode.NOT_BOARD_POST_AUTHOR);
        assertThat(fails(() -> service.delete(OTHER_ID, POST_ID)).getErrorCode())
                .isEqualTo(ErrorCode.NOT_BOARD_POST_AUTHOR);

        BoardPost deleted = post(BoardCategory.TIP, null);
        deleted.delete(LocalDateTime.of(2026, 11, 1, 0, 0));
        givenLocked(deleted);
        assertThat(fails(() -> service.delete(AUTHOR_ID, POST_ID)).getErrorCode())
                .isEqualTo(ErrorCode.BOARD_POST_NOT_FOUND);
    }

    @Test
    @DisplayName("삭제는 소프트 삭제 (BRD-003 ④)")
    void 삭제() {
        BoardPost post = post(BoardCategory.TIP, null);
        givenLocked(post);
        LocalDateTime now = LocalDateTime.of(2026, 11, 23, 12, 0);

        service.delete(AUTHOR_ID, POST_ID, now);

        assertThat(post.getDeletedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("좋아요: 처음 누르면 +1, 이미 눌렀으면 그대로 성공 (BRD-007 ②)")
    void 좋아요() {
        BoardPost post = post(BoardCategory.TIP, null);
        givenLocked(post);
        given(boardPostRepository.insertLike(POST_ID, OTHER_ID)).willReturn(1, 0);

        assertThat(service.like(OTHER_ID, POST_ID)).isEqualTo(new BoardPostLikeResponse(1, true));
        assertThat(service.like(OTHER_ID, POST_ID)).isEqualTo(new BoardPostLikeResponse(1, true));
    }

    @Test
    @DisplayName("좋아요 취소: 눌렀으면 -1, 안 눌렀으면 그대로 성공")
    void 좋아요_취소() {
        BoardPost post = post(BoardCategory.TIP, null);
        post.increaseLikeCount();
        givenLocked(post);
        given(boardPostRepository.deleteLike(POST_ID, OTHER_ID)).willReturn(1, 0);

        assertThat(service.unlike(OTHER_ID, POST_ID))
                .isEqualTo(new BoardPostLikeResponse(0, false));
        assertThat(service.unlike(OTHER_ID, POST_ID))
                .isEqualTo(new BoardPostLikeResponse(0, false));
    }

    @Test
    @DisplayName("내 글에는 좋아요를 누를 수 없다: 403 CANNOT_LIKE_OWN_BOARD_POST (BRD-007 ③)")
    void 내_글_좋아요_금지() {
        givenLocked(post(BoardCategory.TIP, null));

        assertThat(fails(() -> service.like(AUTHOR_ID, POST_ID)).getErrorCode())
                .isEqualTo(ErrorCode.CANNOT_LIKE_OWN_BOARD_POST);
        verify(boardPostRepository, never()).insertLike(anyLong(), anyLong());
    }

    @Test
    @DisplayName("삭제된 글에는 좋아요를 누를 수 없다: 404")
    void 삭제된_글_좋아요() {
        BoardPost post = post(BoardCategory.TIP, null);
        post.delete(LocalDateTime.of(2026, 11, 1, 0, 0));
        givenLocked(post);

        assertThat(fails(() -> service.like(OTHER_ID, POST_ID)).getErrorCode())
                .isEqualTo(ErrorCode.BOARD_POST_NOT_FOUND);
    }
}
