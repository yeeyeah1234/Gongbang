package com.gongbang.workspace.whiteboard.service;

import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.domain.Member;
import com.gongbang.member.service.MemberService;
import com.gongbang.team.dto.TeamMemberResponse;
import com.gongbang.team.service.TeamMemberManagementService;
import com.gongbang.team.service.TeamService;
import com.gongbang.workspace.whiteboard.domain.Whiteboard;
import com.gongbang.workspace.whiteboard.domain.WhiteboardChangeType;
import com.gongbang.workspace.whiteboard.domain.WhiteboardChangedEvent;
import com.gongbang.workspace.whiteboard.dto.WhiteboardAccessResponse;
import com.gongbang.workspace.whiteboard.repository.WhiteboardContentRepository;
import com.gongbang.workspace.whiteboard.repository.WhiteboardRepository;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 동기화 서버(sync-server, Hocuspocus)가 부르는 화이트보드 API (WB-10). 동기화 서버는 MySQL에 직접 붙지 않고 이 API로만 입장 확인·본문
 * 불러오기·저장을 한다 (AGENTS.md 2-2).
 *
 * <ul>
 *   <li>입장 확인은 API 명세서대로 {@code GET /api/whiteboards/{id}/access}: 동기화 서버가 사용자 토큰을 그대로 넘긴다.
 *   <li>본문 불러오기·저장은 {@code /internal/whiteboards/**}: X-Sync-Key 확인은 {@code SyncKeyFilter}가 끝낸다.
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class WhiteboardSyncService {

    private final WhiteboardRepository whiteboardRepository;
    private final WhiteboardContentRepository whiteboardContentRepository;
    private final TeamMemberManagementService teamMemberManagementService;
    private final TeamService teamService;
    private final MemberService memberService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * WB-10 입장 확인. 순서: 활동 회원이 아니면 401 UNAUTHORIZED → 보드가 없거나 삭제됐으면 404 RESOURCE_NOT_FOUND → 그 팀의 현재
     * 팀원이 아니면 403 NOT_TEAM_MEMBER (팀원 목록 조회가 확인).
     *
     * <p>colorIndex는 team 공통 모듈을 고치지 않으려고 팀원 목록(NET-06 {@code getMembers})에서 찾는다.
     */
    @Transactional(readOnly = true)
    public WhiteboardAccessResponse checkAccess(Long memberId, Long whiteboardId) {
        if (memberId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        Member member =
                memberService
                        .findActiveMember(memberId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        Whiteboard whiteboard = findWhiteboard(whiteboardId);
        Long teamId = whiteboard.getTeamId();
        int colorIndex =
                teamMemberManagementService.getMembers(teamId, memberId).stream()
                        .filter(teamMember -> Objects.equals(teamMember.memberId(), memberId))
                        .findFirst()
                        .map(TeamMemberResponse::colorIndex)
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_TEAM_MEMBER));

        return new WhiteboardAccessResponse(
                whiteboardId, teamId, memberId, member.getNickname(), colorIndex);
    }

    /** WB-10 본문 불러오기. 없거나 삭제됐으면 404. 본문이 NULL이거나 비었으면 빈 값(컨트롤러가 204). */
    @Transactional(readOnly = true)
    public Optional<byte[]> loadContent(Long whiteboardId) {
        findWhiteboard(whiteboardId);
        byte[] content = whiteboardContentRepository.findContent(whiteboardId);
        return content == null || content.length == 0 ? Optional.empty() : Optional.of(content);
    }

    /**
     * WB-10 본문 저장 (DOC-10과 같은 규칙). 바이트를 그대로 저장하고 last_editor_id·updated_at, 팀 최근 활동 시각을 갱신한 뒤
     * UPDATED를 발행한다. 없거나 삭제됐으면 404.
     *
     * <p>lastEditorId가 지금 팀원인지는 보지 않는다 ({@code DocumentSyncService.saveContent}와 같은 이유: 여러 사람의 편집이
     * 합쳐진 상태라 막으면 다른 팀원의 편집까지 잃는다).
     */
    @Transactional
    public void saveContent(Long whiteboardId, Long lastEditorId, byte[] content) {
        if (lastEditorId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        Whiteboard whiteboard = findWhiteboard(whiteboardId);
        byte[] bytes = content == null ? new byte[0] : content;
        if (whiteboardContentRepository.saveContent(whiteboardId, bytes, lastEditorId) == 0) {
            // 찾은 뒤 저장 전에 삭제된 경우
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        teamService.touchLastActivity(whiteboard.getTeamId(), LocalDateTime.now());
        eventPublisher.publishEvent(
                new WhiteboardChangedEvent(
                        whiteboard.getTeamId(),
                        whiteboardId,
                        WhiteboardChangeType.UPDATED,
                        lastEditorId));
    }

    private Whiteboard findWhiteboard(Long whiteboardId) {
        return whiteboardRepository
                .findByIdAndDeletedAtIsNull(whiteboardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
