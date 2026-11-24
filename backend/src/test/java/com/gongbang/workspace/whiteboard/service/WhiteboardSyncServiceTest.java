package com.gongbang.workspace.whiteboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.domain.Member;
import com.gongbang.member.service.MemberService;
import com.gongbang.team.domain.TeamRole;
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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/** 입장 확인 순서(401 → 404 → 403)와 colorIndex, 본문 204·404, 저장 후 활동 시각·이벤트를 본다. */
@ExtendWith(MockitoExtension.class)
class WhiteboardSyncServiceTest {

    private static final Long TEAM_ID = 1L;
    private static final Long MEMBER_ID = 10L;
    private static final Long OTHER_ID = 20L;
    private static final Long WHITEBOARD_ID = 600L;
    private static final byte[] BYTES = {0x00, 0x01, (byte) 0xFF};

    @Mock private WhiteboardRepository whiteboardRepository;
    @Mock private WhiteboardContentRepository whiteboardContentRepository;
    @Mock private TeamMemberManagementService teamMemberManagementService;
    @Mock private TeamService teamService;
    @Mock private MemberService memberService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private WhiteboardSyncService whiteboardSyncService;

    @BeforeEach
    void setUp() {
        whiteboardSyncService =
                new WhiteboardSyncService(
                        whiteboardRepository,
                        whiteboardContentRepository,
                        teamMemberManagementService,
                        teamService,
                        memberService,
                        eventPublisher);
    }

    private void givenWhiteboard() {
        Whiteboard whiteboard = Whiteboard.create(TEAM_ID, MEMBER_ID);
        ReflectionTestUtils.setField(whiteboard, "id", WHITEBOARD_ID);
        when(whiteboardRepository.findByIdAndDeletedAtIsNull(WHITEBOARD_ID))
                .thenReturn(Optional.of(whiteboard));
    }

    private void givenNoWhiteboard() {
        when(whiteboardRepository.findByIdAndDeletedAtIsNull(WHITEBOARD_ID))
                .thenReturn(Optional.empty());
    }

    private Member givenActiveMember() {
        Member member = mock(Member.class);
        when(memberService.findActiveMember(MEMBER_ID)).thenReturn(Optional.of(member));
        return member;
    }

    private static TeamMemberResponse teamMember(Long memberId, int colorIndex) {
        return new TeamMemberResponse(
                memberId, "닉네임", null, TeamRole.MEMBER, colorIndex, List.of());
    }

    private static void assertErrorCode(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(expected);
    }

    // ---------- access ----------

    @Test
    @DisplayName("access: 팀원이면 whiteboardId·teamId·memberId·닉네임·내 colorIndex를 돌려준다")
    void access_성공() {
        when(givenActiveMember().getNickname()).thenReturn("규선");
        givenWhiteboard();
        when(teamMemberManagementService.getMembers(TEAM_ID, MEMBER_ID))
                .thenReturn(List.of(teamMember(OTHER_ID, 1), teamMember(MEMBER_ID, 3)));

        WhiteboardAccessResponse response =
                whiteboardSyncService.checkAccess(MEMBER_ID, WHITEBOARD_ID);

        assertThat(response)
                .isEqualTo(
                        new WhiteboardAccessResponse(WHITEBOARD_ID, TEAM_ID, MEMBER_ID, "규선", 3));
    }

    @Test
    @DisplayName("access: 회원을 모르면 401 UNAUTHORIZED, 보드를 읽지 않는다")
    void access_토큰없음_401() {
        assertErrorCode(
                () -> whiteboardSyncService.checkAccess(null, WHITEBOARD_ID),
                ErrorCode.UNAUTHORIZED);
        verifyNoInteractions(memberService, whiteboardRepository, teamMemberManagementService);
    }

    @Test
    @DisplayName("access: 탈퇴·삭제된 회원이면 401 UNAUTHORIZED")
    void access_탈퇴회원_401() {
        when(memberService.findActiveMember(MEMBER_ID)).thenReturn(Optional.empty());

        assertErrorCode(
                () -> whiteboardSyncService.checkAccess(MEMBER_ID, WHITEBOARD_ID),
                ErrorCode.UNAUTHORIZED);
        verifyNoInteractions(whiteboardRepository);
    }

    @Test
    @DisplayName("access: 없거나 삭제된 보드는 팀원 확인 전에 404")
    void access_보드없음_404() {
        givenActiveMember();
        givenNoWhiteboard();

        assertErrorCode(
                () -> whiteboardSyncService.checkAccess(MEMBER_ID, WHITEBOARD_ID),
                ErrorCode.RESOURCE_NOT_FOUND);
        verifyNoInteractions(teamMemberManagementService);
    }

    @Test
    @DisplayName("access: 그 보드 팀의 현재 팀원이 아니면 403 NOT_TEAM_MEMBER")
    void access_팀원아님_403() {
        givenActiveMember();
        givenWhiteboard();
        doThrow(new BusinessException(ErrorCode.NOT_TEAM_MEMBER))
                .when(teamMemberManagementService)
                .getMembers(TEAM_ID, MEMBER_ID);

        assertErrorCode(
                () -> whiteboardSyncService.checkAccess(MEMBER_ID, WHITEBOARD_ID),
                ErrorCode.NOT_TEAM_MEMBER);
    }

    // ---------- content 불러오기 ----------

    @Test
    @DisplayName("불러오기: 본문 바이트를 그대로 돌려준다")
    void 불러오기_성공() {
        givenWhiteboard();
        when(whiteboardContentRepository.findContent(WHITEBOARD_ID)).thenReturn(BYTES);

        assertThat(whiteboardSyncService.loadContent(WHITEBOARD_ID)).contains(BYTES);
    }

    @Test
    @DisplayName("불러오기: 본문이 NULL이거나 빈 배열이면 빈 값 (컨트롤러가 204)")
    void 불러오기_비었음() {
        givenWhiteboard();
        when(whiteboardContentRepository.findContent(WHITEBOARD_ID))
                .thenReturn(null)
                .thenReturn(new byte[0]);

        assertThat(whiteboardSyncService.loadContent(WHITEBOARD_ID)).isEmpty();
        assertThat(whiteboardSyncService.loadContent(WHITEBOARD_ID)).isEmpty();
    }

    @Test
    @DisplayName("불러오기: 없거나 삭제된 보드는 404, 본문을 읽지 않는다")
    void 불러오기_삭제됨_404() {
        givenNoWhiteboard();

        assertErrorCode(
                () -> whiteboardSyncService.loadContent(WHITEBOARD_ID),
                ErrorCode.RESOURCE_NOT_FOUND);
        verifyNoInteractions(whiteboardContentRepository);
    }

    // ---------- content 저장 ----------

    @Test
    @DisplayName("저장: 바이트를 저장하고 팀 최근 활동 시각을 갱신하고 UPDATED(actor = lastEditorId)를 발행한다")
    void 저장_성공() {
        givenWhiteboard();
        when(whiteboardContentRepository.saveContent(WHITEBOARD_ID, BYTES, OTHER_ID)).thenReturn(1);

        whiteboardSyncService.saveContent(WHITEBOARD_ID, OTHER_ID, BYTES);

        verify(teamService).touchLastActivity(eq(TEAM_ID), any(LocalDateTime.class));
        verify(eventPublisher)
                .publishEvent(
                        new WhiteboardChangedEvent(
                                TEAM_ID, WHITEBOARD_ID, WhiteboardChangeType.UPDATED, OTHER_ID));
    }

    @Test
    @DisplayName("저장: 본문이 없으면(null) 빈 배열로 저장한다")
    void 저장_null은_빈_배열() {
        givenWhiteboard();
        when(whiteboardContentRepository.saveContent(
                        eq(WHITEBOARD_ID), any(byte[].class), eq(MEMBER_ID)))
                .thenReturn(1);

        whiteboardSyncService.saveContent(WHITEBOARD_ID, MEMBER_ID, null);

        verify(whiteboardContentRepository).saveContent(WHITEBOARD_ID, new byte[0], MEMBER_ID);
    }

    @Test
    @DisplayName("저장: 없거나 삭제된 보드는 404, 저장·활동 갱신·발행하지 않는다")
    void 저장_삭제됨_404() {
        givenNoWhiteboard();

        assertErrorCode(
                () -> whiteboardSyncService.saveContent(WHITEBOARD_ID, MEMBER_ID, BYTES),
                ErrorCode.RESOURCE_NOT_FOUND);
        verifyNoInteractions(whiteboardContentRepository, teamService, eventPublisher);
    }

    @Test
    @DisplayName("저장: 찾은 뒤 저장 전에 삭제돼 바뀐 행이 0이면 404, 활동 갱신·발행하지 않는다")
    void 저장_경합으로_0행이면_404() {
        givenWhiteboard();
        when(whiteboardContentRepository.saveContent(WHITEBOARD_ID, BYTES, MEMBER_ID))
                .thenReturn(0);

        assertErrorCode(
                () -> whiteboardSyncService.saveContent(WHITEBOARD_ID, MEMBER_ID, BYTES),
                ErrorCode.RESOURCE_NOT_FOUND);
        verifyNoInteractions(teamService, eventPublisher);
    }

    @Test
    @DisplayName("저장: lastEditorId가 없으면 400 INVALID_INPUT")
    void 저장_lastEditorId_없으면_400() {
        assertErrorCode(
                () -> whiteboardSyncService.saveContent(WHITEBOARD_ID, null, BYTES),
                ErrorCode.INVALID_INPUT);
        verifyNoInteractions(whiteboardRepository, whiteboardContentRepository);
    }
}
