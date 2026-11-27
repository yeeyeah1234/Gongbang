package com.gongbang.admin.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gongbang.board.service.BoardPostModerationService;
import com.gongbang.comment.service.CommentModerationService;
import com.gongbang.contest.service.AdminContestService;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.service.*;
import com.gongbang.notification.service.ServiceNoticeService;
import com.gongbang.post.service.AdminPostService;
import com.gongbang.report.service.AdminReportService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class AdminActivityTargetServiceTest {
    final AdminMemberService access = mock(AdminMemberService.class);
    final BoardPostModerationService boards = mock(BoardPostModerationService.class);
    final AdminActivityTargetService service =
            new AdminActivityTargetService(
                    access,
                    mock(MemberService.class),
                    mock(AdminPostService.class),
                    boards,
                    mock(CommentModerationService.class),
                    mock(AdminReportService.class),
                    mock(AdminContestService.class),
                    mock(ServiceNoticeService.class),
                    mock(AdminInvitationService.class),
                    mock(VerificationReviewService.class));

    @Test
    void 일반회원은삭제된원문을조회할수없다() {
        doThrow(new BusinessException(ErrorCode.NOT_ADMIN)).when(access).requireAdmin(1L);
        assertThatThrownBy(() -> service.detail(1L, "BOARD_POST", 8L))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(boards);
    }

    @Test
    void 삭제된글은관리자에게내용을보여주되공개링크를주지않는다() {
        when(boards.target(1L, 8L))
                .thenReturn(
                        new BoardPostModerationService.Target(
                                2L, "title", "body", LocalDateTime.now()));
        var result = service.detail(1L, "BOARD_POST", 8L);
        assertThat(result.title()).isEqualTo("title");
        assertThat(result.content()).isEqualTo("body");
        assertThat(result.url()).isNull();
    }

    @Test
    void 공개글은원문으로연결한다() {
        when(boards.target(1L, 8L))
                .thenReturn(new BoardPostModerationService.Target(2L, "title", "body", null));
        assertThat(service.detail(1L, "BOARD_POST", 8L).url()).isEqualTo("/board/8");
    }
}
