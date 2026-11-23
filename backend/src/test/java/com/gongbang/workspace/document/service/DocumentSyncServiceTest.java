package com.gongbang.workspace.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/** 동기화 서버용 권한 확인 순서(401 → 404 → 403), 본문 204·404, 저장 후 활동 시각·이벤트를 본다. */
@ExtendWith(MockitoExtension.class)
class DocumentSyncServiceTest {

    private static final Long TEAM_ID = 1L;
    private static final Long MEMBER_ID = 10L;
    private static final Long DOCUMENT_ID = 500L;
    private static final byte[] BYTES = {0x00, 0x01, (byte) 0xFF};

    @Mock private DocumentRepository documentRepository;
    @Mock private DocumentContentRepository documentContentRepository;
    @Mock private TeamMemberService teamMemberService;
    @Mock private TeamService teamService;
    @Mock private MemberService memberService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private DocumentSyncService documentSyncService;

    @BeforeEach
    void setUp() {
        documentSyncService =
                new DocumentSyncService(
                        documentRepository,
                        documentContentRepository,
                        teamMemberService,
                        teamService,
                        memberService,
                        eventPublisher);
    }

    private void givenDocument() {
        Document document = Document.create(TEAM_ID, MEMBER_ID);
        ReflectionTestUtils.setField(document, "id", DOCUMENT_ID);
        when(documentRepository.findByIdAndDeletedAtIsNull(DOCUMENT_ID))
                .thenReturn(Optional.of(document));
    }

    private void givenNoDocument() {
        when(documentRepository.findByIdAndDeletedAtIsNull(DOCUMENT_ID))
                .thenReturn(Optional.empty());
    }

    /** 닉네임은 성공 경로에서만 읽으므로 거기서 따로 stub한다 (strict stubs). */
    private Member givenActiveMember() {
        Member member = mock(Member.class);
        when(memberService.findActiveMember(MEMBER_ID)).thenReturn(Optional.of(member));
        return member;
    }

    private static void assertErrorCode(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(expected);
    }

    // ---------- access ----------

    @Test
    @DisplayName("access: 팀원이면 memberId·닉네임·teamId·팀장 여부를 돌려준다")
    void access_성공() {
        when(givenActiveMember().getNickname()).thenReturn("상현");
        givenDocument();
        when(teamMemberService.isLeader(TEAM_ID, MEMBER_ID)).thenReturn(true);

        DocumentAccessResponse response = documentSyncService.checkAccess(MEMBER_ID, DOCUMENT_ID);

        assertThat(response).isEqualTo(new DocumentAccessResponse(MEMBER_ID, "상현", TEAM_ID, true));
        verify(teamMemberService).requireMember(TEAM_ID, MEMBER_ID);
    }

    @Test
    @DisplayName("access: 토큰이 없거나 틀려 회원을 모르면 401 UNAUTHORIZED, 문서를 읽지 않는다")
    void access_토큰없음_401() {
        assertErrorCode(
                () -> documentSyncService.checkAccess(null, DOCUMENT_ID), ErrorCode.UNAUTHORIZED);
        verifyNoInteractions(memberService, documentRepository, teamMemberService);
    }

    @Test
    @DisplayName("access: 탈퇴·삭제된 회원이면 401 UNAUTHORIZED")
    void access_탈퇴회원_401() {
        when(memberService.findActiveMember(MEMBER_ID)).thenReturn(Optional.empty());

        assertErrorCode(
                () -> documentSyncService.checkAccess(MEMBER_ID, DOCUMENT_ID),
                ErrorCode.UNAUTHORIZED);
        verifyNoInteractions(documentRepository);
    }

    @Test
    @DisplayName("access: 없거나 삭제된 문서는 팀원 확인 전에 404")
    void access_문서없음_404() {
        givenActiveMember();
        givenNoDocument();

        assertErrorCode(
                () -> documentSyncService.checkAccess(MEMBER_ID, DOCUMENT_ID),
                ErrorCode.RESOURCE_NOT_FOUND);
        verifyNoInteractions(teamMemberService);
    }

    @Test
    @DisplayName("access: 그 문서 팀의 현재 팀원이 아니면 403 NOT_TEAM_MEMBER")
    void access_팀원아님_403() {
        givenActiveMember();
        givenDocument();
        doThrow(new BusinessException(ErrorCode.NOT_TEAM_MEMBER))
                .when(teamMemberService)
                .requireMember(TEAM_ID, MEMBER_ID);

        assertErrorCode(
                () -> documentSyncService.checkAccess(MEMBER_ID, DOCUMENT_ID),
                ErrorCode.NOT_TEAM_MEMBER);
        verify(teamMemberService, never()).isLeader(anyLong(), anyLong());
    }

    // ---------- content 불러오기 ----------

    @Test
    @DisplayName("불러오기: 본문 바이트를 그대로 돌려준다")
    void 불러오기_성공() {
        givenDocument();
        when(documentContentRepository.findContent(DOCUMENT_ID)).thenReturn(BYTES);

        assertThat(documentSyncService.loadContent(DOCUMENT_ID)).contains(BYTES);
    }

    @Test
    @DisplayName("불러오기: 본문이 NULL이거나 빈 배열이면 빈 값 (컨트롤러가 204)")
    void 불러오기_비었음() {
        givenDocument();
        when(documentContentRepository.findContent(DOCUMENT_ID))
                .thenReturn(null)
                .thenReturn(new byte[0]);

        assertThat(documentSyncService.loadContent(DOCUMENT_ID)).isEmpty();
        assertThat(documentSyncService.loadContent(DOCUMENT_ID)).isEmpty();
    }

    @Test
    @DisplayName("불러오기: 없거나 삭제된 문서는 404, 본문을 읽지 않는다")
    void 불러오기_삭제됨_404() {
        givenNoDocument();

        assertErrorCode(
                () -> documentSyncService.loadContent(DOCUMENT_ID), ErrorCode.RESOURCE_NOT_FOUND);
        verifyNoInteractions(documentContentRepository);
    }

    // ---------- content 저장 ----------

    @Test
    @DisplayName("저장: 바이트를 저장하고 팀 최근 활동 시각을 갱신하고 SAVED를 발행한다")
    void 저장_성공() {
        givenDocument();
        when(documentContentRepository.saveContent(DOCUMENT_ID, BYTES, MEMBER_ID)).thenReturn(1);

        documentSyncService.saveContent(DOCUMENT_ID, MEMBER_ID, BYTES);

        verify(teamService).touchLastActivity(eq(TEAM_ID), any(LocalDateTime.class));
        verify(eventPublisher)
                .publishEvent(
                        new DocumentChangedEvent(TEAM_ID, DOCUMENT_ID, DocumentChangeType.SAVED));
    }

    @Test
    @DisplayName("저장: 본문이 없으면(null) 빈 배열로 저장한다")
    void 저장_null은_빈_배열() {
        givenDocument();
        when(documentContentRepository.saveContent(
                        eq(DOCUMENT_ID), any(byte[].class), eq(MEMBER_ID)))
                .thenReturn(1);

        documentSyncService.saveContent(DOCUMENT_ID, MEMBER_ID, null);

        verify(documentContentRepository).saveContent(DOCUMENT_ID, new byte[0], MEMBER_ID);
    }

    @Test
    @DisplayName("저장: 없거나 삭제된 문서는 404, 저장·활동 갱신·발행하지 않는다")
    void 저장_삭제됨_404() {
        givenNoDocument();

        assertErrorCode(
                () -> documentSyncService.saveContent(DOCUMENT_ID, MEMBER_ID, BYTES),
                ErrorCode.RESOURCE_NOT_FOUND);
        verifyNoInteractions(documentContentRepository, teamService, eventPublisher);
    }

    @Test
    @DisplayName("저장: 찾은 뒤 저장 전에 삭제돼 바뀐 행이 0이면 404, 활동 갱신·발행하지 않는다")
    void 저장_경합으로_0행이면_404() {
        givenDocument();
        when(documentContentRepository.saveContent(DOCUMENT_ID, BYTES, MEMBER_ID)).thenReturn(0);

        assertErrorCode(
                () -> documentSyncService.saveContent(DOCUMENT_ID, MEMBER_ID, BYTES),
                ErrorCode.RESOURCE_NOT_FOUND);
        verifyNoInteractions(teamService, eventPublisher);
    }

    @Test
    @DisplayName("저장: editorId가 없으면 400 INVALID_INPUT")
    void 저장_editorId_없으면_400() {
        assertErrorCode(
                () -> documentSyncService.saveContent(DOCUMENT_ID, null, BYTES),
                ErrorCode.INVALID_INPUT);
        verifyNoInteractions(documentRepository, documentContentRepository);
    }
}
