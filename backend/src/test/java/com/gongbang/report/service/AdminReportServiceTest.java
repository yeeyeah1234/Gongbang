package com.gongbang.report.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gongbang.chat.service.ChatReportService;
import com.gongbang.comment.service.CommentModerationService;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.service.*;
import com.gongbang.post.service.AdminPostService;
import com.gongbang.report.domain.*;
import com.gongbang.report.repository.ReportRepository;
import jakarta.persistence.*;
import org.junit.jupiter.api.Test;

class AdminReportServiceTest {
    final EntityManager em = mock(EntityManager.class);
    final CommentModerationService comments = mock(CommentModerationService.class);
    final AdminMemberManagementService members = mock(AdminMemberManagementService.class);
    final com.gongbang.board.service.BoardPostModerationService boardPosts =
            mock(com.gongbang.board.service.BoardPostModerationService.class);
    final AdminReportService service =
            new AdminReportService(
                    mock(com.gongbang.admin.service.AdminActivityWriter.class),
                    mock(AdminMemberService.class),
                    members,
                    mock(MemberService.class),
                    mock(AdminPostService.class),
                    comments,
                    mock(ReportRepository.class),
                    em,
                    mock(ChatReportService.class),
                    boardPosts);

    Report report() {
        var r = Report.create(1L, 2L, ReportTargetType.COMMENT, 9L, ReportReasonCode.ABUSE, "내용");
        when(em.find(Report.class, 8L)).thenReturn(r);
        return r;
    }

    @Test
    void 게시글숨김은_게시글서비스에_위임하고_중복처리를_거부한다() {
        var r =
                Report.create(
                        1L, 2L, ReportTargetType.BOARD_POST, 9L, ReportReasonCode.ABUSE, "내용");
        when(em.find(Report.class, 8L)).thenReturn(r);
        when(boardPosts.target(5L, 9L))
                .thenReturn(
                        new com.gongbang.board.service.BoardPostModerationService.Target(
                                2L, "제목", "내용", null));
        var hiddenAt = java.time.LocalDateTime.of(2026, 11, 27, 9, 0);
        when(boardPosts.hideForReview(5L, 9L)).thenReturn(hiddenAt);
        service.review(5L, 8L, new AdminReportService.Review("HIDE_BOARD_POST", "부적절한 내용", null));
        verify(boardPosts).hideForReview(5L, 9L);
        assertThat(r.getAppliedHiddenAt()).isEqualTo(hiddenAt);
        assertThat(r.isHideProvenanceKnown()).isTrue();
        assertThat(r.getStatus()).isEqualTo(ReportStatus.RESOLVED);
        assertThatThrownBy(
                        () ->
                                service.review(
                                        5L,
                                        8L,
                                        new AdminReportService.Review(
                                                "HIDE_BOARD_POST", "재처리", null)))
                .isInstanceOf(BusinessException.class);
        verify(boardPosts, times(1)).hideForReview(5L, 9L);
    }

    @Test
    void 기각은_제재없이_처리자와시각을기록한다() {
        var r = report();
        service.review(5L, 8L, new AdminReportService.Review("DISMISS", "근거 없음", null));
        assertThat(r.getStatus()).isEqualTo(ReportStatus.DISMISSED);
        assertThat(r.getReviewerId()).isEqualTo(5L);
        assertThat(r.getReviewedAt()).isNotNull();
        verify(comments, never()).hideForReview(any(), any());
        verifyNoInteractions(members);
    }

    @Test
    void 댓글숨김후_처리완료가되며_중복처리를거부한다() {
        var r = report();
        when(comments.target(5L, 9L))
                .thenReturn(new CommentModerationService.Item(9L, 7L, 2L, "내용", null));
        service.review(5L, 8L, new AdminReportService.Review("HIDE_COMMENT", "욕설", null));
        assertThat(r.getStatus()).isEqualTo(ReportStatus.RESOLVED);
        verify(comments).hideForReview(5L, 9L);
        assertThatThrownBy(
                        () ->
                                service.review(
                                        5L,
                                        8L,
                                        new AdminReportService.Review("DISMISS", "사유", null)))
                .isInstanceOf(BusinessException.class);
        verify(comments, times(1)).hideForReview(5L, 9L);
    }

    @Test
    void 조치실패시_신고상태를완료로바꾸지않는다() {
        var r = report();
        when(comments.target(5L, 9L))
                .thenReturn(new CommentModerationService.Item(9L, 7L, 2L, "내용", null));
        doThrow(new IllegalStateException("db failure")).when(comments).hideForReview(5L, 9L);
        assertThatThrownBy(
                        () ->
                                service.review(
                                        5L,
                                        8L,
                                        new AdminReportService.Review("HIDE_COMMENT", "사유", null)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(r.getStatus()).isEqualTo(ReportStatus.RECEIVED);
    }
}
