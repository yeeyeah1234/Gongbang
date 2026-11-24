package com.gongbang.workspace.whiteboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.dto.MemberProfileSummary;
import com.gongbang.member.service.MemberService;
import com.gongbang.team.service.TeamMemberService;
import com.gongbang.workspace.whiteboard.dto.WhiteboardListItemResponse;
import com.gongbang.workspace.whiteboard.dto.WhiteboardListItemResponse.LastEditor;
import com.gongbang.workspace.whiteboard.repository.WhiteboardRepository;
import com.gongbang.workspace.whiteboard.repository.WhiteboardSummary;
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

/** 목록의 lastEditor(없으면 null, 탈퇴면 「탈퇴한 사용자」)와 팀원 확인을 본다. 정렬·삭제 제외 SQL은 WhiteboardMysqlTest에서 본다. */
@ExtendWith(MockitoExtension.class)
class WhiteboardQueryServiceTest {

    private static final Long TEAM_ID = 1L;
    private static final Long ME = 10L;
    private static final Long OTHER_ID = 20L;
    private static final Long GONE_ID = 30L;
    private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 24, 10, 0);

    @Mock private WhiteboardRepository whiteboardRepository;
    @Mock private TeamMemberService teamMemberService;
    @Mock private MemberService memberService;

    private WhiteboardQueryService whiteboardQueryService;

    @BeforeEach
    void setUp() {
        whiteboardQueryService =
                new WhiteboardQueryService(whiteboardRepository, teamMemberService, memberService);
    }

    @Test
    @DisplayName("목록: repository 순서대로, 마지막 수정자 닉네임을 채우고 저장한 적 없으면 null, 못 찾으면 「탈퇴한 사용자」")
    void 목록_팀원() {
        when(whiteboardRepository.findSummaries(TEAM_ID))
                .thenReturn(
                        List.of(
                                new WhiteboardSummary(3L, "저장됨", ME, OTHER_ID, AT),
                                new WhiteboardSummary(2L, "새 보드", OTHER_ID, null, AT),
                                new WhiteboardSummary(1L, "탈퇴자 수정", ME, GONE_ID, AT)));
        when(memberService.getProfileSummaries(Set.of(OTHER_ID, GONE_ID)))
                .thenReturn(
                        Map.of(
                                OTHER_ID,
                                new MemberProfileSummary(OTHER_ID, "상현", null, List.of())));

        List<WhiteboardListItemResponse> result =
                whiteboardQueryService.getWhiteboards(ME, TEAM_ID);

        assertThat(result)
                .containsExactly(
                        new WhiteboardListItemResponse(
                                3L, "저장됨", ME, new LastEditor(OTHER_ID, "상현"), AT),
                        new WhiteboardListItemResponse(2L, "새 보드", OTHER_ID, null, AT),
                        new WhiteboardListItemResponse(
                                1L, "탈퇴자 수정", ME, new LastEditor(GONE_ID, "탈퇴한 사용자"), AT));
    }

    @Test
    @DisplayName("목록: 아무도 본문을 저장하지 않았으면 회원 정보를 읽지 않는다")
    void 목록_수정자_없음() {
        when(whiteboardRepository.findSummaries(TEAM_ID))
                .thenReturn(List.of(new WhiteboardSummary(1L, "제목 없는 보드", ME, null, AT)));

        assertThat(whiteboardQueryService.getWhiteboards(ME, TEAM_ID))
                .singleElement()
                .extracting(WhiteboardListItemResponse::lastEditor)
                .isNull();
        verifyNoInteractions(memberService);
    }

    @Test
    @DisplayName("목록: 보드가 없으면 빈 배열")
    void 목록_비었음() {
        when(whiteboardRepository.findSummaries(TEAM_ID)).thenReturn(List.of());

        assertThat(whiteboardQueryService.getWhiteboards(ME, TEAM_ID)).isEmpty();
        verifyNoInteractions(memberService);
    }

    @Test
    @DisplayName("목록: 팀원이 아니면 403 NOT_TEAM_MEMBER")
    void 목록_팀원_아니면_403() {
        doThrow(new BusinessException(ErrorCode.NOT_TEAM_MEMBER))
                .when(teamMemberService)
                .requireMember(TEAM_ID, ME);

        assertThatThrownBy(() -> whiteboardQueryService.getWhiteboards(ME, TEAM_ID))
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_TEAM_MEMBER);
        verifyNoInteractions(whiteboardRepository);
    }
}
