package com.gongbang.workspace.document.service;

import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.team.service.TeamMemberService;
import com.gongbang.team.service.TeamService;
import com.gongbang.workspace.DefaultTitleGenerator;
import com.gongbang.workspace.document.domain.Document;
import com.gongbang.workspace.document.domain.DocumentChangeType;
import com.gongbang.workspace.document.domain.DocumentChangedEvent;
import com.gongbang.workspace.document.dto.DocumentTitleResponse;
import com.gongbang.workspace.document.repository.DocumentRepository;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 팀 문서 생성·이름 변경·삭제 (DOC-02~04). 목록은 {@link DocumentQueryService}, 동기화 서버용 본문 불러오기·저장은 {@code
 * DocumentSyncService}.
 *
 * <p>권한 순서는 캘린더와 같다: 문서가 없거나 삭제됐으면 404 RESOURCE_NOT_FOUND → 그 팀의 현재 팀원이 아니면 403 NOT_TEAM_MEMBER →
 * (삭제만) 작성자도 팀장도 아니면 403 NOT_DOCUMENT_AUTHOR.
 *
 * <p>50개 제한은 AGENTS.md 5·7에 따라 409 DOCUMENT_LIMIT이고, 팀 행을 잠근 같은 트랜잭션 안에서 센다. 생성만은 잠금을 가장 먼저 하므로 순서가
 * 없는 팀 404 → 팀원 아님 403 → 50개 409다 ({@link #create} 주석).
 *
 * <p>이벤트는 트랜잭션 안에서 발행한다. 받는 쪽은 AFTER_COMMIT으로 받는다 ({@link DocumentChangedEvent}).
 */
@Service
@RequiredArgsConstructor
public class DocumentService {

    /** 팀당 문서 최대 개수 (DOC-02, AGENTS.md 7). 삭제된 문서는 세지 않는다. */
    static final int MAX_DOCUMENTS_PER_TEAM = 50;

    private final DocumentRepository documentRepository;
    private final TeamMemberService teamMemberService;
    private final TeamService teamService;
    private final ApplicationEventPublisher eventPublisher;

    /** DOC-02 생성. 새 문서 ID와 제목을 돌려준다. */
    @Transactional
    public DocumentTitleResponse create(Long memberId, Long teamId) {
        // 동시에 만들어도 50개를 넘지 않게 팀 행을 잠근 뒤 센다 (AGENTS.md 7).
        // 잠금이 이 트랜잭션의 첫 SQL이어야 한다: MySQL REPEATABLE READ는 첫 일반 SELECT 때 스냅숏을 잡고, FOR UPDATE는
        // 스냅숏을 다시 잡지 않는다. 팀원 확인을 먼저 하면 잠금을 기다리기 전의 스냅숏으로 세어 50개를 넘는다.
        // 그래서 없는 팀은 팀원 확인보다 먼저 404 RESOURCE_NOT_FOUND가 된다.
        teamService.lockTeam(teamId);
        teamMemberService.requireMember(teamId, memberId);
        if (documentRepository.countByTeamIdAndDeletedAtIsNull(teamId) >= MAX_DOCUMENTS_PER_TEAM) {
            throw new BusinessException(ErrorCode.DOCUMENT_LIMIT);
        }

        String title =
                DefaultTitleGenerator.nextTitle(
                        Document.DEFAULT_TITLE,
                        documentRepository.findDefaultTitles(teamId, Document.DEFAULT_TITLE));
        Document document = documentRepository.save(Document.create(teamId, memberId, title));
        publish(document, DocumentChangeType.CREATED);
        return new DocumentTitleResponse(document.getId(), document.getTitle());
    }

    /** DOC-03 이름 변경. 제목이 실제로 바뀌었을 때만 RENAMED를 발행한다. */
    @Transactional
    public Long rename(Long memberId, Long documentId, String title) {
        Document document = findDocument(documentId);
        teamMemberService.requireMember(document.getTeamId(), memberId);

        if (document.rename(normalizeTitle(title), memberId)) {
            publish(document, DocumentChangeType.RENAMED);
        }
        return document.getId();
    }

    /** DOC-04 삭제: 작성자 또는 팀장만. deleted_at을 기록한다 (소프트 삭제). */
    @Transactional
    public void delete(Long memberId, Long documentId) {
        Document document = findDocument(documentId);
        Long teamId = document.getTeamId();
        teamMemberService.requireMember(teamId, memberId);
        // 작성자 본인이면 팀장 여부를 읽지 않는다
        boolean author = Objects.equals(document.getCreatedBy(), memberId);
        if (!author && !teamMemberService.isLeader(teamId, memberId)) {
            throw new BusinessException(ErrorCode.NOT_DOCUMENT_AUTHOR);
        }

        document.softDelete(LocalDateTime.now());
        publish(document, DocumentChangeType.DELETED);
    }

    private Document findDocument(Long documentId) {
        return documentRepository
                .findByIdAndDeletedAtIsNull(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private void publish(Document document, DocumentChangeType type) {
        eventPublisher.publishEvent(
                new DocumentChangedEvent(document.getTeamId(), document.getId(), type));
    }

    /**
     * 앞뒤 공백을 지운다. null·빈 값·공백뿐이면 「제목 없는 문서」. 50자를 넘으면 400 INVALID_INPUT. 글자 수는 DB(utf8mb4)처럼
     * 문자(코드포인트) 단위로 센다 ({@code CalendarEventService.requireText}와 같은 규칙).
     */
    static String normalizeTitle(String title) {
        if (!StringUtils.hasText(title)) {
            return Document.DEFAULT_TITLE;
        }
        String stripped = title.strip();
        if (stripped.codePointCount(0, stripped.length()) > Document.TITLE_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return stripped;
    }
}
