package com.gongbang.workspace.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.team.service.TeamMemberService;
import com.gongbang.team.service.TeamService;
import com.gongbang.workspace.document.domain.Document;
import com.gongbang.workspace.document.domain.DocumentChangeType;
import com.gongbang.workspace.document.domain.DocumentChangedEvent;
import com.gongbang.workspace.document.dto.DocumentTitleResponse;
import com.gongbang.workspace.document.repository.DocumentRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/** 50개 제한·제목 규칙·삭제 권한·삭제된 문서 404·이벤트를 본다. 실제 저장·정렬·동시 생성은 DocumentMysqlTest에서 본다. */
@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    private static final Long TEAM_ID = 1L;
    private static final Long AUTHOR_ID = 10L;
    private static final Long OTHER_ID = 20L;
    private static final Long LEADER_ID = 30L;
    private static final Long DOCUMENT_ID = 500L;

    @Mock private DocumentRepository documentRepository;
    @Mock private TeamMemberService teamMemberService;
    @Mock private TeamService teamService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private DocumentService documentService;

    @BeforeEach
    void setUp() {
        documentService =
                new DocumentService(
                        documentRepository, teamMemberService, teamService, eventPublisher);
    }

    // ---------- 준비 ----------

    private static Document document(Long createdBy) {
        Document document = Document.create(TEAM_ID, createdBy);
        ReflectionTestUtils.setField(document, "id", DOCUMENT_ID);
        return document;
    }

    private Document givenDocument() {
        Document document = document(AUTHOR_ID);
        when(documentRepository.findByIdAndDeletedAtIsNull(DOCUMENT_ID))
                .thenReturn(Optional.of(document));
        return document;
    }

    /** 없거나 삭제된 문서: repository가 deleted_at IS NULL로 거르므로 둘 다 빈 값이다. */
    private void givenNoDocument() {
        when(documentRepository.findByIdAndDeletedAtIsNull(DOCUMENT_ID))
                .thenReturn(Optional.empty());
    }

    private void givenSaveAssignsId() {
        when(documentRepository.save(any(Document.class)))
                .thenAnswer(
                        invocation -> {
                            Document saved = invocation.getArgument(0);
                            ReflectionTestUtils.setField(saved, "id", DOCUMENT_ID);
                            return saved;
                        });
    }

    private DocumentChangedEvent capturePublished() {
        ArgumentCaptor<DocumentChangedEvent> captor =
                ArgumentCaptor.forClass(DocumentChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return captor.getValue();
    }

    private static void assertErrorCode(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(expected);
    }

    // ---------- 생성 (DOC-02) ----------

    @Test
    @DisplayName("생성: 49개면 「제목 없는 문서」로 만들고 CREATED를 발행한다. 팀 행을 잠근 뒤 센다")
    void 생성_성공() {
        when(documentRepository.countByTeamIdAndDeletedAtIsNull(TEAM_ID)).thenReturn(49L);
        when(documentRepository.findDefaultTitles(TEAM_ID, Document.DEFAULT_TITLE))
                .thenReturn(List.of());
        givenSaveAssignsId();

        DocumentTitleResponse response = documentService.create(AUTHOR_ID, TEAM_ID);

        assertThat(response).isEqualTo(new DocumentTitleResponse(DOCUMENT_ID, "제목 없는 문서"));
        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(captor.capture());
        Document saved = captor.getValue();
        assertThat(saved.getTeamId()).isEqualTo(TEAM_ID);
        assertThat(saved.getTitle()).isEqualTo("제목 없는 문서");
        assertThat(saved.getCreatedBy()).isEqualTo(AUTHOR_ID);
        assertThat(saved.getUpdatedBy()).isEqualTo(AUTHOR_ID);
        assertThat(saved.isDeleted()).isFalse();
        assertThat(capturePublished())
                .isEqualTo(
                        new DocumentChangedEvent(TEAM_ID, DOCUMENT_ID, DocumentChangeType.CREATED));

        // 잠금이 첫 SQL이어야 REPEATABLE READ 스냅숏이 잠금 뒤에 잡힌다 (DocumentMysqlTest 동시 생성)
        InOrder order = inOrder(teamMemberService, teamService, documentRepository);
        order.verify(teamService).lockTeam(TEAM_ID);
        order.verify(teamMemberService).requireMember(TEAM_ID, AUTHOR_ID);
        order.verify(documentRepository).countByTeamIdAndDeletedAtIsNull(TEAM_ID);
        order.verify(documentRepository).findDefaultTitles(TEAM_ID, Document.DEFAULT_TITLE);
        order.verify(documentRepository).save(any());
    }

    @Test
    @DisplayName("생성: 기존 기본 제목이 있으면 가장 큰 번호 + 1로 만든다")
    void 생성_번호_증가() {
        when(documentRepository.countByTeamIdAndDeletedAtIsNull(TEAM_ID)).thenReturn(2L);
        when(documentRepository.findDefaultTitles(TEAM_ID, Document.DEFAULT_TITLE))
                .thenReturn(List.of("제목 없는 문서", "제목 없는 문서 (4)", "제목 없는 문서 (복사본)"));
        givenSaveAssignsId();

        DocumentTitleResponse response = documentService.create(AUTHOR_ID, TEAM_ID);

        assertThat(response).isEqualTo(new DocumentTitleResponse(DOCUMENT_ID, "제목 없는 문서 (5)"));
    }

    @Test
    @DisplayName("생성: 이미 50개면 409 DOCUMENT_LIMIT, 저장·발행하지 않는다")
    void 생성_50개면_409() {
        when(documentRepository.countByTeamIdAndDeletedAtIsNull(TEAM_ID)).thenReturn(50L);

        assertErrorCode(() -> documentService.create(AUTHOR_ID, TEAM_ID), ErrorCode.DOCUMENT_LIMIT);
        assertThat(ErrorCode.DOCUMENT_LIMIT.getHttpStatus().value()).isEqualTo(409);
        verify(documentRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("생성: 팀원이 아니면 (잠근 뒤) 403 NOT_TEAM_MEMBER, 세거나 저장하지 않는다")
    void 생성_팀원_아니면_403() {
        doThrow(new BusinessException(ErrorCode.NOT_TEAM_MEMBER))
                .when(teamMemberService)
                .requireMember(TEAM_ID, OTHER_ID);

        assertErrorCode(() -> documentService.create(OTHER_ID, TEAM_ID), ErrorCode.NOT_TEAM_MEMBER);
        verify(teamService).lockTeam(TEAM_ID);
        verifyNoInteractions(documentRepository, eventPublisher);
    }

    @Test
    @DisplayName("생성: 없는 팀이면 팀원 확인 전에 404 RESOURCE_NOT_FOUND")
    void 생성_없는_팀이면_404() {
        doThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND))
                .when(teamService)
                .lockTeam(TEAM_ID);

        assertErrorCode(
                () -> documentService.create(AUTHOR_ID, TEAM_ID), ErrorCode.RESOURCE_NOT_FOUND);
        verifyNoInteractions(teamMemberService, documentRepository, eventPublisher);
    }

    // ---------- 이름 변경 (DOC-03) ----------

    @Test
    @DisplayName("이름 변경: 앞뒤 공백을 지워 저장하고, 바꾼 사람을 updated_by로, RENAMED를 발행한다")
    void 이름변경_성공() {
        Document document = givenDocument();

        Long id = documentService.rename(OTHER_ID, DOCUMENT_ID, "  회의록  ");

        assertThat(id).isEqualTo(DOCUMENT_ID);
        assertThat(document.getTitle()).isEqualTo("회의록");
        assertThat(document.getUpdatedBy()).isEqualTo(OTHER_ID);
        verify(teamMemberService).requireMember(TEAM_ID, OTHER_ID);
        assertThat(capturePublished())
                .isEqualTo(
                        new DocumentChangedEvent(TEAM_ID, DOCUMENT_ID, DocumentChangeType.RENAMED));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    @DisplayName("이름 변경: null·빈 값·공백뿐이면 「제목 없는 문서」")
    void 이름변경_빈_값이면_기본_제목(String title) {
        Document document = givenDocument();
        document.rename("기존 제목", AUTHOR_ID);

        documentService.rename(AUTHOR_ID, DOCUMENT_ID, title);

        assertThat(document.getTitle()).isEqualTo("제목 없는 문서");
    }

    @Test
    @DisplayName("이름 변경: 50자는 되고 51자는 400 INVALID_INPUT (이모지는 1자로 센다)")
    void 이름변경_길이() {
        Document document = givenDocument();
        String fifty = "😀".repeat(50);

        documentService.rename(AUTHOR_ID, DOCUMENT_ID, fifty);
        assertThat(document.getTitle()).isEqualTo(fifty);

        assertErrorCode(
                () -> documentService.rename(AUTHOR_ID, DOCUMENT_ID, "가".repeat(51)),
                ErrorCode.INVALID_INPUT);
        assertThat(document.getTitle()).isEqualTo(fifty);
    }

    @Test
    @DisplayName("이름 변경: 앞뒤 공백을 지운 뒤 50자면 된다")
    void 이름변경_공백은_길이에_넣지_않는다() {
        Document document = givenDocument();

        documentService.rename(AUTHOR_ID, DOCUMENT_ID, "  " + "a".repeat(50) + "  ");

        assertThat(document.getTitle()).isEqualTo("a".repeat(50));
    }

    @Test
    @DisplayName("이름 변경: 같은 제목이면 바꾸지 않고 발행하지 않는다")
    void 이름변경_같은_제목이면_발행_안_함() {
        Document document = givenDocument();

        documentService.rename(OTHER_ID, DOCUMENT_ID, " 제목 없는 문서 ");

        assertThat(document.getUpdatedBy()).isEqualTo(AUTHOR_ID);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("이름 변경: 없거나 삭제된 문서는 404 RESOURCE_NOT_FOUND (팀원 확인 전에)")
    void 이름변경_없는_문서_404() {
        givenNoDocument();

        assertErrorCode(
                () -> documentService.rename(AUTHOR_ID, DOCUMENT_ID, "제목"),
                ErrorCode.RESOURCE_NOT_FOUND);
        verifyNoInteractions(teamMemberService, eventPublisher);
    }

    @Test
    @DisplayName("이름 변경: 팀원이 아니면 403 NOT_TEAM_MEMBER")
    void 이름변경_팀원_아니면_403() {
        Document document = givenDocument();
        doThrow(new BusinessException(ErrorCode.NOT_TEAM_MEMBER))
                .when(teamMemberService)
                .requireMember(TEAM_ID, OTHER_ID);

        assertErrorCode(
                () -> documentService.rename(OTHER_ID, DOCUMENT_ID, "제목"),
                ErrorCode.NOT_TEAM_MEMBER);
        assertThat(document.getTitle()).isEqualTo("제목 없는 문서");
    }

    // ---------- 삭제 (DOC-04) ----------

    @Test
    @DisplayName("삭제: 작성자는 지울 수 있고(팀장 여부를 읽지 않음) deleted_at을 기록하고 DELETED를 발행한다")
    void 삭제_작성자() {
        Document document = givenDocument();

        documentService.delete(AUTHOR_ID, DOCUMENT_ID);

        assertThat(document.isDeleted()).isTrue();
        verify(teamMemberService, never()).isLeader(anyLong(), anyLong());
        verify(documentRepository, never()).delete(any());
        assertThat(capturePublished())
                .isEqualTo(
                        new DocumentChangedEvent(TEAM_ID, DOCUMENT_ID, DocumentChangeType.DELETED));
    }

    @Test
    @DisplayName("삭제: 작성자가 아니어도 팀장은 지울 수 있다")
    void 삭제_팀장() {
        Document document = givenDocument();
        when(teamMemberService.isLeader(TEAM_ID, LEADER_ID)).thenReturn(true);

        documentService.delete(LEADER_ID, DOCUMENT_ID);

        assertThat(document.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("삭제: 작성자도 팀장도 아닌 팀원은 403 NOT_DOCUMENT_AUTHOR")
    void 삭제_다른_팀원_403() {
        Document document = givenDocument();
        when(teamMemberService.isLeader(TEAM_ID, OTHER_ID)).thenReturn(false);

        assertErrorCode(
                () -> documentService.delete(OTHER_ID, DOCUMENT_ID), ErrorCode.NOT_DOCUMENT_AUTHOR);
        assertThat(ErrorCode.NOT_DOCUMENT_AUTHOR.getHttpStatus().value()).isEqualTo(403);
        assertThat(document.isDeleted()).isFalse();
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("삭제: 팀원이 아니면 작성자 확인 전에 403 NOT_TEAM_MEMBER")
    void 삭제_팀원_아니면_403() {
        givenDocument();
        doThrow(new BusinessException(ErrorCode.NOT_TEAM_MEMBER))
                .when(teamMemberService)
                .requireMember(TEAM_ID, AUTHOR_ID);

        assertErrorCode(
                () -> documentService.delete(AUTHOR_ID, DOCUMENT_ID), ErrorCode.NOT_TEAM_MEMBER);
        verify(teamMemberService, never()).isLeader(anyLong(), anyLong());
    }

    @Test
    @DisplayName("삭제: 없거나 이미 삭제된 문서는 404 RESOURCE_NOT_FOUND")
    void 삭제_없는_문서_404() {
        givenNoDocument();

        assertErrorCode(
                () -> documentService.delete(AUTHOR_ID, DOCUMENT_ID), ErrorCode.RESOURCE_NOT_FOUND);
        verifyNoInteractions(teamMemberService, eventPublisher);
    }
}
