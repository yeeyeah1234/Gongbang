package com.gongbang.comment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.gongbang.board.domain.BoardCategory;
import com.gongbang.board.domain.BoardPost;
import com.gongbang.board.service.BoardPostQueryService;
import com.gongbang.comment.domain.Comment;
import com.gongbang.comment.dto.CommentResponse;
import com.gongbang.comment.repository.CommentRepository;
import com.gongbang.member.dto.MemberProfileSummary;
import com.gongbang.member.service.MemberService;
import com.gongbang.post.domain.RecruitPost;
import com.gongbang.post.service.PostQueryService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CommentQueryServiceTest {

    @Mock private CommentRepository commentRepository;
    @Mock private PostQueryService postQueryService;
    @Mock private BoardPostQueryService boardPostQueryService;
    @Mock private MemberService memberService;

    private CommentQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService =
                new CommentQueryService(
                        commentRepository,
                        postQueryService,
                        boardPostQueryService,
                        new CommentMapper(memberService));
    }

    @Test
    @DisplayName("비공개 댓글은 글 작성자·댓글 작성자에게만 본문을 보여 주고 제3자·비회원에게는 null로 마스킹한다")
    void 비공개_댓글_마스킹() {
        RecruitPost post = sampleRecruitPost(10L, 1L);
        Comment secretRoot = Comment.forPost(10L, 2L, null, "비공개 질문", true);
        setMeta(secretRoot, 100L, LocalDateTime.of(2026, 11, 24, 10, 0));
        Comment secretReplyByPostAuthor = Comment.forPost(10L, 1L, 100L, "비공개 답변", true);
        setMeta(secretReplyByPostAuthor, 101L, LocalDateTime.of(2026, 11, 24, 10, 5));

        given(postQueryService.getActivePost(10L)).willReturn(post);
        given(commentRepository.findByPostIdOrderByCreatedAtAscIdAsc(10L))
                .willReturn(List.of(secretRoot, secretReplyByPostAuthor));
        given(memberService.getProfileSummaries(Set.of(2L, 1L)))
                .willReturn(
                        Map.of(
                                1L, new MemberProfileSummary(1L, "팀장", null, List.of()),
                                2L, new MemberProfileSummary(2L, "지원자", null, List.of())));

        // 비회원 조회: 원댓글·답글 모두 content=null
        List<CommentResponse> guestView = queryService.getPostComments(10L, null);
        assertThat(guestView).hasSize(1);
        assertThat(guestView.get(0).isSecret()).isTrue();
        assertThat(guestView.get(0).content()).isNull();
        assertThat(guestView.get(0).replies()).hasSize(1);
        assertThat(guestView.get(0).replies().get(0).content()).isNull();
        assertThat(guestView.get(0).replies().get(0).isPostAuthor()).isTrue();

        // 원댓글 작성자(2L) 조회: 본인 비밀 원댓글과 그에 달린 비밀 답글 모두 열람 가능
        List<CommentResponse> commentAuthorView = queryService.getPostComments(10L, 2L);
        assertThat(commentAuthorView.get(0).content()).isEqualTo("비공개 질문");
        assertThat(commentAuthorView.get(0).replies().get(0).content()).isEqualTo("비공개 답변");

        // 글 작성자(1L) 조회: 모두 열람 가능
        List<CommentResponse> postAuthorView = queryService.getPostComments(10L, 1L);
        assertThat(postAuthorView.get(0).content()).isEqualTo("비공개 질문");
        assertThat(postAuthorView.get(0).replies().get(0).content()).isEqualTo("비공개 답변");
    }

    @Test
    @DisplayName("답글이 남은 삭제 원댓글은 isDeleted=true로 남기고, 답글이 없는 삭제 원댓글과 삭제된 답글은 숨긴다")
    void 삭제된_댓글_표시_규칙() {
        BoardPost boardPost = BoardPost.of(1L, BoardCategory.TIP, null, "노하우 공유", "본문", null);
        ReflectionTestUtils.setField(boardPost, "id", 20L);

        Comment deletedWithReply = Comment.forBoardPost(20L, 2L, null, "지워질 원댓글", false);
        setMeta(deletedWithReply, 200L, LocalDateTime.of(2026, 11, 24, 11, 0));
        deletedWithReply.delete(LocalDateTime.of(2026, 11, 24, 11, 10));

        Comment activeReply = Comment.forBoardPost(20L, 3L, 200L, "남아있는 대댓글", false);
        setMeta(activeReply, 201L, LocalDateTime.of(2026, 11, 24, 11, 5));

        Comment deletedAlone = Comment.forBoardPost(20L, 4L, null, "혼자 지워진 댓글", false);
        setMeta(deletedAlone, 202L, LocalDateTime.of(2026, 11, 24, 11, 6));
        deletedAlone.delete(LocalDateTime.of(2026, 11, 24, 11, 12));

        given(boardPostQueryService.getActivePost(20L)).willReturn(boardPost);
        given(commentRepository.findByBoardPostIdOrderByCreatedAtAscIdAsc(20L))
                .willReturn(List.of(deletedWithReply, activeReply, deletedAlone));
        given(memberService.getProfileSummaries(Set.of(3L)))
                .willReturn(Map.of(3L, new MemberProfileSummary(3L, "진우", "/p.png", List.of())));

        List<CommentResponse> tree = queryService.getBoardPostComments(20L, null);

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).commentId()).isEqualTo(200L);
        assertThat(tree.get(0).isDeleted()).isTrue();
        assertThat(tree.get(0).author()).isNull();
        assertThat(tree.get(0).content()).isNull();
        assertThat(tree.get(0).replies()).hasSize(1);
        assertThat(tree.get(0).replies().get(0).commentId()).isEqualTo(201L);
        assertThat(tree.get(0).replies().get(0).content()).isEqualTo("남아있는 대댓글");
    }

    private static RecruitPost sampleRecruitPost(Long postId, Long authorId) {
        RecruitPost post = mock(RecruitPost.class);
        given(post.getAuthorId()).willReturn(authorId);
        return post;
    }

    private static void setMeta(Comment comment, Long id, LocalDateTime time) {
        ReflectionTestUtils.setField(comment, "id", id);
        ReflectionTestUtils.setField(comment, "createdAt", time);
        ReflectionTestUtils.setField(comment, "updatedAt", time);
    }
}
