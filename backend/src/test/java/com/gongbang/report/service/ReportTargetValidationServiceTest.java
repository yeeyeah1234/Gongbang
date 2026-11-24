package com.gongbang.report.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gongbang.chat.service.ChatReportService;
import com.gongbang.comment.service.CommentModerationService;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.post.service.AdminPostService;
import com.gongbang.report.domain.*;
import com.gongbang.report.dto.ReportCreateRequest;
import org.junit.jupiter.api.Test;

class ReportTargetValidationServiceTest {
    final CommentModerationService comments = mock(CommentModerationService.class);
    final AdminPostService posts = mock(AdminPostService.class);
    final ChatReportService chat = mock(ChatReportService.class);
    final ReportTargetValidationService service =
            new ReportTargetValidationService(comments, posts, chat);

    @Test
    void 다른사람의댓글로_무관한회원을신고할수없다() {
        when(comments.reportAuthor(8L)).thenReturn(3L);
        assertThatThrownBy(
                        () ->
                                service.validate(
                                        1L,
                                        new ReportCreateRequest(
                                                2L,
                                                ReportTargetType.COMMENT,
                                                8L,
                                                ReportReasonCode.ABUSE,
                                                null)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 권한없는채팅메시지를_신고할수없다() {
        when(chat.read(1L, 8L, false))
                .thenThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        assertThatThrownBy(
                        () ->
                                service.validate(
                                        1L,
                                        new ReportCreateRequest(
                                                2L,
                                                ReportTargetType.MESSAGE,
                                                8L,
                                                ReportReasonCode.ABUSE,
                                                null)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 실제작성자가일치하는모집글은_신고가능하다() {
        when(posts.reportAuthor(8L)).thenReturn(2L);
        assertThatCode(
                        () ->
                                service.validate(
                                        1L,
                                        new ReportCreateRequest(
                                                2L,
                                                ReportTargetType.POST,
                                                8L,
                                                ReportReasonCode.ABUSE,
                                                null)))
                .doesNotThrowAnyException();
    }
}
