package com.gongbang.report.service;

import com.gongbang.chat.service.ChatReportService;
import com.gongbang.comment.service.CommentModerationService;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.post.service.AdminPostService;
import com.gongbang.report.dto.ReportCreateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReportTargetValidationService {
    private final CommentModerationService comments;
    private final AdminPostService posts;
    private final ChatReportService chat;
    private final com.gongbang.board.service.BoardPostModerationService boardPosts;

    public void validate(Long actor, ReportCreateRequest body) {
        Long owner =
                switch (body.targetType()) {
                    case MEMBER -> body.reportedMemberId();
                    case COMMENT -> comments.reportAuthor(body.targetId());
                    case POST -> posts.reportAuthor(body.targetId());
                    case BOARD_POST -> boardPosts.reportAuthor(body.targetId());
                    case MESSAGE -> chat.read(actor, body.targetId(), false).authorId();
                };
        if (owner == null || !owner.equals(body.reportedMemberId()))
            throw new BusinessException(ErrorCode.INVALID_INPUT, "신고 대상과 작성자가 일치하지 않습니다");
    }
}
