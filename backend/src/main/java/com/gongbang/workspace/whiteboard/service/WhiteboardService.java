package com.gongbang.workspace.whiteboard.service;

import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.team.service.TeamMemberService;
import com.gongbang.team.service.TeamService;
import com.gongbang.workspace.whiteboard.domain.Whiteboard;
import com.gongbang.workspace.whiteboard.domain.WhiteboardChangeType;
import com.gongbang.workspace.whiteboard.domain.WhiteboardChangedEvent;
import com.gongbang.workspace.whiteboard.dto.WhiteboardTitleResponse;
import com.gongbang.workspace.whiteboard.repository.WhiteboardRepository;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 팀 화이트보드 만들기·제목 변경·삭제 (WB-01). 목록은 {@link WhiteboardQueryService}, 동기화 서버용은 {@link
 * WhiteboardSyncService}.
 *
 * <p>권한 순서는 문서(DOC-02~04)와 같다: 보드가 없거나 삭제됐으면 404 RESOURCE_NOT_FOUND → 그 팀의 현재 팀원이 아니면 403
 * NOT_TEAM_MEMBER → (삭제만) 만든 사람도 팀장도 아니면 403 NOT_WHITEBOARD_AUTHOR.
 *
 * <p>30개 제한은 409 WHITEBOARD_LIMIT이고, 팀 행을 잠근 같은 트랜잭션 안에서 센다 (AGENTS.md 7).
 */
@Service
@RequiredArgsConstructor
public class WhiteboardService {

    /** 팀당 보드 최대 개수 (WB-01 ③, 제안값). 삭제된 보드는 세지 않는다. */
    static final int MAX_WHITEBOARDS_PER_TEAM = 30;

    private final WhiteboardRepository whiteboardRepository;
    private final TeamMemberService teamMemberService;
    private final TeamService teamService;
    private final ApplicationEventPublisher eventPublisher;

    /** WB-01 ② 만들기. */
    @Transactional
    public WhiteboardTitleResponse create(Long memberId, Long teamId) {
        // 동시에 만들어도 30개를 넘지 않게 팀 행을 잠근 뒤 센다 (AGENTS.md 7).
        // 잠금이 이 트랜잭션의 첫 SQL이어야 한다: MySQL REPEATABLE READ는 첫 일반 SELECT 때 스냅숏을 잡고, FOR UPDATE는
        // 스냅숏을 다시 잡지 않는다. 팀원 확인을 먼저 하면 잠금을 기다리기 전의 스냅숏으로 세어 30개를 넘는다
        // (DocumentService.create와 같음). 그래서 없는 팀은 팀원 확인보다 먼저 404 RESOURCE_NOT_FOUND가 된다.
        teamService.lockTeam(teamId);
        teamMemberService.requireMember(teamId, memberId);
        if (whiteboardRepository.countByTeamIdAndDeletedAtIsNull(teamId)
                >= MAX_WHITEBOARDS_PER_TEAM) {
            throw new BusinessException(ErrorCode.WHITEBOARD_LIMIT);
        }

        Whiteboard whiteboard = whiteboardRepository.save(Whiteboard.create(teamId, memberId));
        publish(whiteboard, WhiteboardChangeType.CREATED, memberId);
        return new WhiteboardTitleResponse(whiteboard.getId(), whiteboard.getTitle());
    }

    /** WB-01 ① 제목 변경 (팀원 누구나, DOC-03과 같음). 제목이 실제로 바뀌었을 때만 UPDATED를 발행한다. */
    @Transactional
    public WhiteboardTitleResponse rename(Long memberId, Long whiteboardId, String title) {
        Whiteboard whiteboard = findWhiteboard(whiteboardId);
        teamMemberService.requireMember(whiteboard.getTeamId(), memberId);

        if (whiteboard.rename(normalizeTitle(title))) {
            publish(whiteboard, WhiteboardChangeType.UPDATED, memberId);
        }
        return new WhiteboardTitleResponse(whiteboard.getId(), whiteboard.getTitle());
    }

    /** WB-01 ① 삭제: 만든 사람 또는 팀장만. deleted_at을 기록한다 (소프트 삭제). */
    @Transactional
    public void delete(Long memberId, Long whiteboardId) {
        Whiteboard whiteboard = findWhiteboard(whiteboardId);
        Long teamId = whiteboard.getTeamId();
        teamMemberService.requireMember(teamId, memberId);
        // 만든 사람 본인이면 팀장 여부를 읽지 않는다
        boolean creator = Objects.equals(whiteboard.getCreatorId(), memberId);
        if (!creator && !teamMemberService.isLeader(teamId, memberId)) {
            throw new BusinessException(ErrorCode.NOT_WHITEBOARD_AUTHOR);
        }

        whiteboard.softDelete(LocalDateTime.now());
        publish(whiteboard, WhiteboardChangeType.DELETED, memberId);
    }

    private Whiteboard findWhiteboard(Long whiteboardId) {
        return whiteboardRepository
                .findByIdAndDeletedAtIsNull(whiteboardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private void publish(Whiteboard whiteboard, WhiteboardChangeType type, Long actorId) {
        eventPublisher.publishEvent(
                new WhiteboardChangedEvent(
                        whiteboard.getTeamId(), whiteboard.getId(), type, actorId));
    }

    /**
     * 앞뒤 공백을 지운다. null·빈 값·공백뿐이면 「제목 없는 보드」. 50자를 넘으면 400 INVALID_INPUT. 글자 수는 DB(utf8mb4)처럼
     * 문자(코드포인트) 단위로 센다 ({@code DocumentService.normalizeTitle}과 같은 규칙).
     */
    static String normalizeTitle(String title) {
        if (!StringUtils.hasText(title)) {
            return Whiteboard.DEFAULT_TITLE;
        }
        String stripped = title.strip();
        if (stripped.codePointCount(0, stripped.length()) > Whiteboard.TITLE_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return stripped;
    }
}
