package com.gongbang.comment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gongbang.board.domain.BoardCategory;
import com.gongbang.board.domain.BoardPost;
import com.gongbang.board.service.BoardPostQueryService;
import com.gongbang.comment.domain.Comment;
import com.gongbang.comment.dto.CommentCreateRequest;
import com.gongbang.comment.dto.CommentCreateResponse;
import com.gongbang.comment.dto.CommentResponse;
import com.gongbang.comment.dto.CommentUpdateRequest;
import com.gongbang.comment.repository.CommentRepository;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.dto.MemberProfileSummary;
import com.gongbang.member.service.MemberService;
import com.gongbang.notification.domain.NotificationType;
import com.gongbang.notification.service.NotificationService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CommentCommandServiceTest {

    @Mock private CommentRepository commentRepository;
    @Mock private com.gongbang.post.service.PostQueryService postQueryService;
    @Mock private BoardPostQueryService boardPostQueryService;
    @Mock private MemberService memberService;
    @Mock private NotificationService notificationService;

    private CommentCommandService commandService;

    @BeforeEach
    void setUp() {
        commandService =
                new CommentCommandService(
                        commentRepository,
                        postQueryService,
                        boardPostQueryService,
                        new CommentMapper(memberService),
                        new CommentNotifier(notificationService, memberService));
    }

    @Test
    @DisplayName("커뮤니티 게시글에 대댓글 작성 시 저장하고 원댓글 작성자에게 REPLY, 글 작성자에게 COMMENT 알림을 보낸다")
    void 대댓글_작성_및_알림() {
        BoardPost post = BoardPost.of(1L, BoardCategory.CONTEST, 3L, "공모전 후기", "내용", null);
        ReflectionTestUtils.setField(post, "id", 20L);

        Comment parent = Comment.forBoardPost(20L, 2L, null, "원댓글", false);
        ReflectionTestUtils.setField(parent, "id", 100L);

        given(boardPostQueryService.getActivePost(20L)).willReturn(post);
        given(commentRepository.findById(100L)).willReturn(Optional.of(parent));
        given(memberService.displayName(3L)).willReturn("건우");
        given(commentRepository.save(any(Comment.class)))
                .willAnswer(
                        inv -> {
                            Comment c = inv.getArgument(0);
                            ReflectionTestUtils.setField(c, "id", 101L);
                            return c;
                        });

        CommentCreateResponse res =
                commandService.createBoardPostComment(
                        20L, 3L, new CommentCreateRequest("답글입니다", false, 100L));

        assertThat(res.commentId()).isEqualTo(101L);
        verify(notificationService)
                .send(
                        eq(2L),
                        eq(NotificationType.REPLY),
                        eq(3L),
                        eq("BOARD_POST"),
                        eq(20L),
                        any(),
                        eq("/board/20"));
        verify(notificationService)
                .send(
                        eq(1L),
                        eq(NotificationType.COMMENT),
                        eq(3L),
                        eq("BOARD_POST"),
                        eq(20L),
                        any(),
                        eq("/board/20"));
    }

    @Test
    @DisplayName("답글에 다시 답글을 달려고 하면 REPLY_DEPTH_EXCEEDED를 던진다")
    void 대댓글의_대댓글_거부() {
        BoardPost post = BoardPost.of(1L, BoardCategory.TIP, null, "제목", "내용", null);
        ReflectionTestUtils.setField(post, "id", 20L);

        Comment reply = Comment.forBoardPost(20L, 2L, 100L, "이미 답글", false);
        ReflectionTestUtils.setField(reply, "id", 101L);

        given(boardPostQueryService.getActivePost(20L)).willReturn(post);
        given(commentRepository.findById(101L)).willReturn(Optional.of(reply));

        assertThatThrownBy(
                        () ->
                                commandService.createBoardPostComment(
                                        20L, 3L, new CommentCreateRequest("2단계 답글", false, 101L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REPLY_DEPTH_EXCEEDED);
        verify(commentRepository, never()).save(any());
    }

    @Test
    @DisplayName("공백 또는 500자 초과 댓글은 INVALID_INPUT을 던진다")
    void 입력_길이_검증() {
        BoardPost post = BoardPost.of(1L, BoardCategory.TIP, null, "제목", "내용", null);
        ReflectionTestUtils.setField(post, "id", 20L);
        given(boardPostQueryService.getActivePost(20L)).willReturn(post);

        assertThatThrownBy(
                        () ->
                                commandService.createBoardPostComment(
                                        20L, 3L, new CommentCreateRequest("   ", false, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);

        assertThatThrownBy(
                        () ->
                                commandService.createBoardPostComment(
                                        20L,
                                        3L,
                                        new CommentCreateRequest("가".repeat(501), false, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("작성자가 아니면 수정·삭제 시 NOT_COMMENT_AUTHOR(403)를 던지고, 작성자는 수정·소프트 삭제할 수 있다")
    void 댓글_수정_삭제_권한() {
        BoardPost post = BoardPost.of(1L, BoardCategory.TIP, null, "제목", "내용", null);
        ReflectionTestUtils.setField(post, "id", 20L);

        Comment comment = Comment.forBoardPost(20L, 7L, null, "원본", false);
        ReflectionTestUtils.setField(comment, "id", 300L);
        ReflectionTestUtils.setField(comment, "createdAt", LocalDateTime.of(2026, 11, 24, 12, 0));
        ReflectionTestUtils.setField(comment, "updatedAt", LocalDateTime.of(2026, 11, 24, 12, 0));

        given(commentRepository.findById(300L)).willReturn(Optional.of(comment));
        given(boardPostQueryService.getActivePost(20L)).willReturn(post);

        assertThatThrownBy(
                        () ->
                                commandService.updateComment(
                                        300L, 8L, new CommentUpdateRequest("남의 수정", false)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_COMMENT_AUTHOR);

        assertThatThrownBy(() -> commandService.deleteComment(300L, 8L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_COMMENT_AUTHOR);

        given(commentRepository.findByParentIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(300L))
                .willReturn(List.of());
        given(memberService.getProfileSummaries(Set.of(7L)))
                .willReturn(Map.of(7L, new MemberProfileSummary(7L, "건우", null, List.of())));

        CommentResponse updated =
                commandService.updateComment(300L, 7L, new CommentUpdateRequest("수정본", true));
        assertThat(updated.content()).isEqualTo("수정본");
        assertThat(updated.isSecret()).isTrue();

        commandService.deleteComment(300L, 7L);
        assertThat(comment.isDeleted()).isTrue();
    }
}
