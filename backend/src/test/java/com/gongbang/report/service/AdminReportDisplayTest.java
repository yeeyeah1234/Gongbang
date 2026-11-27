package com.gongbang.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.gongbang.board.service.BoardPostModerationService;
import com.gongbang.chat.service.ChatReportService;
import com.gongbang.comment.service.CommentModerationService;
import com.gongbang.member.service.AdminMemberManagementService;
import com.gongbang.member.service.AdminMemberService;
import com.gongbang.member.service.MemberService;
import com.gongbang.post.service.AdminPostService;
import com.gongbang.report.domain.Report;
import com.gongbang.report.domain.ReportReasonCode;
import com.gongbang.report.domain.ReportTargetType;
import com.gongbang.report.repository.ReportRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AdminReportDisplayTest {
    final AdminMemberService access = mock(AdminMemberService.class);
    final MemberService members = mock(MemberService.class);
    final ReportRepository repository = mock(ReportRepository.class);
    final BoardPostModerationService board = mock(BoardPostModerationService.class);
    final AdminReportService service =
            new AdminReportService(
                    mock(com.gongbang.admin.service.AdminActivityWriter.class),
                    access,
                    mock(AdminMemberManagementService.class),
                    members,
                    mock(AdminPostService.class),
                    mock(CommentModerationService.class),
                    repository,
                    mock(EntityManager.class),
                    mock(ChatReportService.class),
                    board);

    @Test
    void 삭제된글도_제목과작성자_처리자닉네임과현재권한을보여준다() {
        var report =
                Report.create(
                        1L, 2L, ReportTargetType.BOARD_POST, 10L, ReportReasonCode.SPAM, "도배");
        var deleted = LocalDateTime.of(2026, 11, 25, 21, 19);
        report.review("HIDE_BOARD_POST", "사유", 3L, deleted);
        when(repository.findById(5L)).thenReturn(Optional.of(report));
        when(board.target(9L, 10L))
                .thenReturn(new BoardPostModerationService.Target(2L, "게시글 제목", "원문", deleted));
        when(members.displayName(1L)).thenReturn("신고회원");
        when(members.displayName(2L)).thenReturn("작성회원");
        when(members.displayName(3L)).thenReturn("처리관리자");
        when(access.isAdmin(3L)).thenReturn(true);
        var result = service.detail(9L, 5L);
        assertThat(result.report().title()).isEqualTo("게시글 제목");
        assertThat(result.authorNickname()).isEqualTo("작성회원");
        assertThat(result.reporterNickname()).isEqualTo("신고회원");
        assertThat(result.reviewerNickname()).isEqualTo("처리관리자");
        assertThat(result.reviewerAdmin()).isTrue();
        assertThat(result.authorAdmin()).isFalse();
        assertThat(result.deletedAt()).isEqualTo(deleted);
        assertThat(result.content()).contains("원문");
        verify(access).requireAdmin(9L);
        verify(board, times(1)).target(9L, 10L);
    }

    @Test
    void 미처리신고의처리자는없고_작성자가관리자면배지를표시한다() {
        var report =
                Report.create(
                        1L, 2L, ReportTargetType.BOARD_POST, 10L, ReportReasonCode.SPAM, "도배");
        when(repository.findById(5L)).thenReturn(Optional.of(report));
        when(board.target(9L, 10L))
                .thenReturn(new BoardPostModerationService.Target(2L, "제목", "원문", null));
        when(access.isAdmin(2L)).thenReturn(true);
        var result = service.detail(9L, 5L);
        assertThat(result.reviewerNickname()).isNull();
        assertThat(result.reviewerAdmin()).isFalse();
        assertThat(result.authorAdmin()).isTrue();
    }
}
