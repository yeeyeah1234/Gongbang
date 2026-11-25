package com.gongbang.workspace.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.dto.MemberProfileSummary;
import com.gongbang.member.service.MemberService;
import com.gongbang.team.service.TeamMemberService;
import com.gongbang.workspace.document.dto.DocumentListItemResponse;
import com.gongbang.workspace.document.repository.DocumentRepository;
import com.gongbang.workspace.document.repository.DocumentSummary;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 목록의 닉네임·canDelete 계산과 팀원 확인을 본다. 정렬·삭제 제외 SQL은 DocumentMysqlTest에서 본다. */
@ExtendWith(MockitoExtension.class)
class DocumentQueryServiceTest {

    private static final Long TEAM_ID = 1L;
    private static final Long ME = 10L;
    private static final Long OTHER_ID = 20L;
    private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 23, 10, 0);

    @Mock private DocumentRepository documentRepository;
    @Mock private TeamMemberService teamMemberService;
    @Mock private MemberService memberService;

    private DocumentQueryService documentQueryService;

    @BeforeEach
    void setUp() {
        documentQueryService =
                new DocumentQueryService(documentRepository, teamMemberService, memberService);
    }

    @Test
    @DisplayName("목록: 팀원이면 repository 순서대로, 수정한 사람 닉네임과 canDelete(작성자만)를 채운다")
    void 목록_팀원() {
        when(documentRepository.findSummaries(TEAM_ID))
                .thenReturn(
                        List.of(
                                new DocumentSummary(2L, "내 문서", ME, OTHER_ID, AT),
                                new DocumentSummary(1L, "남의 문서", OTHER_ID, ME, AT)));
        when(teamMemberService.isLeader(TEAM_ID, ME)).thenReturn(false);
        when(memberService.getProfileSummaries(Set.of(ME, OTHER_ID)))
                .thenReturn(
                        Map.of(
                                ME,
                                new MemberProfileSummary(ME, "나", null, List.of()),
                                OTHER_ID,
                                new MemberProfileSummary(OTHER_ID, "탈퇴한 사용자", null, List.of())));

        List<DocumentListItemResponse> result = documentQueryService.getDocuments(ME, TEAM_ID);

        assertThat(result)
                .containsExactly(
                        new DocumentListItemResponse(2L, "내 문서", "탈퇴한 사용자", AT, ME, true),
                        new DocumentListItemResponse(1L, "남의 문서", "나", AT, OTHER_ID, false));
    }

    @Test
    @DisplayName("목록: 팀장이면 모든 문서가 canDelete true")
    void 목록_팀장() {
        when(documentRepository.findSummaries(TEAM_ID))
                .thenReturn(List.of(new DocumentSummary(1L, "남의 문서", OTHER_ID, OTHER_ID, AT)));
        when(teamMemberService.isLeader(TEAM_ID, ME)).thenReturn(true);
        // MemberService는 탈퇴·삭제된 회원도 「탈퇴한 사용자」로 채워 준다
        when(memberService.getProfileSummaries(Set.of(OTHER_ID)))
                .thenReturn(
                        Map.of(
                                OTHER_ID,
                                new MemberProfileSummary(OTHER_ID, "탈퇴한 사용자", null, List.of())));

        List<DocumentListItemResponse> result = documentQueryService.getDocuments(ME, TEAM_ID);

        assertThat(result)
                .singleElement()
                .extracting(DocumentListItemResponse::canDelete)
                .isEqualTo(true);
        assertThat(result.get(0).updatedByNickname()).isEqualTo("탈퇴한 사용자");
    }

    @Test
    @DisplayName("목록: 문서가 없으면 빈 배열, 팀장·회원 정보를 읽지 않는다")
    void 목록_비었음() {
        when(documentRepository.findSummaries(TEAM_ID)).thenReturn(List.of());

        assertThat(documentQueryService.getDocuments(ME, TEAM_ID)).isEmpty();
        verify(teamMemberService, never()).isLeader(anyLong(), anyLong());
        verifyNoInteractions(memberService);
    }

    @Test
    @DisplayName("목록: 팀원이 아니면 403 NOT_TEAM_MEMBER")
    void 목록_팀원_아니면_403() {
        doThrow(new BusinessException(ErrorCode.NOT_TEAM_MEMBER))
                .when(teamMemberService)
                .requireMember(TEAM_ID, ME);

        assertThatThrownBy(() -> documentQueryService.getDocuments(ME, TEAM_ID))
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_TEAM_MEMBER);
        verifyNoInteractions(documentRepository);
    }
}
