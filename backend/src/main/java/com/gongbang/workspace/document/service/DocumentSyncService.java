package com.gongbang.workspace.document.service;

import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.domain.Member;
import com.gongbang.member.service.MemberService;
import com.gongbang.team.service.TeamMemberService;
import com.gongbang.team.service.TeamService;
import com.gongbang.workspace.document.domain.Document;
import com.gongbang.workspace.document.domain.DocumentChangeType;
import com.gongbang.workspace.document.domain.DocumentChangedEvent;
import com.gongbang.workspace.document.dto.DocumentAccessResponse;
import com.gongbang.workspace.document.repository.DocumentContentRepository;
import com.gongbang.workspace.document.repository.DocumentRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 동기화 서버(sync-server, Hocuspocus)가 부르는 문서 internal API (DOC-08·DOC-10). X-Sync-Key 확인은 {@code
 * SyncKeyFilter}가 컨트롤러 앞에서 끝낸다.
 *
 * <p>동기화 서버는 MySQL에 직접 붙지 않고 이 API로만 권한 확인·본문 불러오기·저장을 한다 (AGENTS.md 2-2).
 */
@Service
@RequiredArgsConstructor
public class DocumentSyncService {

    private final DocumentRepository documentRepository;
    private final DocumentContentRepository documentContentRepository;
    private final TeamMemberService teamMemberService;
    private final TeamService teamService;
    private final MemberService memberService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * DOC-08 문서 열기 권한. memberId는 동기화 서버가 넘긴 사용자 access 토큰에서 읽은 회원이다.
     *
     * <p>순서: 토큰 없음·틀림(null)이거나 활동 회원이 아니면 401 UNAUTHORIZED (STOMP 연결과 같은 기준) → 문서가 없거나 삭제됐으면 404
     * RESOURCE_NOT_FOUND → 그 팀의 현재 팀원이 아니면 403 NOT_TEAM_MEMBER.
     */
    @Transactional(readOnly = true)
    public DocumentAccessResponse checkAccess(Long memberId, Long documentId) {
        if (memberId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        Member member =
                memberService
                        .findActiveMember(memberId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        Document document = findDocument(documentId);
        Long teamId = document.getTeamId();
        teamMemberService.requireMember(teamId, memberId);

        return new DocumentAccessResponse(
                memberId,
                member.getNickname(),
                teamId,
                teamMemberService.isLeader(teamId, memberId));
    }

    /** DOC-08 본문 불러오기. 없거나 삭제됐으면 404. 본문이 NULL이거나 비었으면 빈 값(컨트롤러가 204). */
    @Transactional(readOnly = true)
    public Optional<byte[]> loadContent(Long documentId) {
        findDocument(documentId);
        byte[] content = documentContentRepository.findContent(documentId);
        return content == null || content.length == 0 ? Optional.empty() : Optional.of(content);
    }

    /**
     * DOC-10 본문 저장. 바이트를 그대로 저장하고 updated_by·updated_at, 팀 최근 활동 시각을 갱신한 뒤 SAVED를 발행한다. 없거나 삭제됐으면
     * 404.
     *
     * <p>editorId가 지금 팀원인지는 보지 않는다. 저장하는 Yjs 상태는 여러 사람의 편집이 합쳐진 것이라, 막으면 다른 팀원의 편집까지 잃는다. 나간 팀원의
     * 연결은 {@code SyncServerClient.disconnect}가 끊는다.
     */
    @Transactional
    public void saveContent(Long documentId, Long editorId, byte[] content) {
        if (editorId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        Document document = findDocument(documentId);
        byte[] bytes = content == null ? new byte[0] : content;
        if (documentContentRepository.saveContent(documentId, bytes, editorId) == 0) {
            // 찾은 뒤 저장 전에 삭제된 경우
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        teamService.touchLastActivity(document.getTeamId(), LocalDateTime.now());
        eventPublisher.publishEvent(
                new DocumentChangedEvent(
                        document.getTeamId(), documentId, DocumentChangeType.SAVED));
    }

    private Document findDocument(Long documentId) {
        return documentRepository
                .findByIdAndDeletedAtIsNull(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
