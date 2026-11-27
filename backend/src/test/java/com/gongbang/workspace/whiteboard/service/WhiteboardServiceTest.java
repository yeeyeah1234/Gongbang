package com.gongbang.workspace.whiteboard.service;

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
import com.gongbang.workspace.whiteboard.domain.Whiteboard;
import com.gongbang.workspace.whiteboard.domain.WhiteboardChangeType;
import com.gongbang.workspace.whiteboard.domain.WhiteboardChangedEvent;
import com.gongbang.workspace.whiteboard.dto.WhiteboardTitleResponse;
import com.gongbang.workspace.whiteboard.repository.WhiteboardRepository;
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

/** 30개 제한·제목 규칙·삭제 권한·삭제된 보드 404·이벤트를 본다. 실제 저장·정렬·동시 생성은 WhiteboardMysqlTest에서 본다. */
@ExtendWith(MockitoExtension.class)
class WhiteboardServiceTest {

    private static final Long TEAM_ID = 1L;
    private static final Long CREATOR_ID = 10L;
    private static final Long OTHER_ID = 20L;
    private static final Long LEADER_ID = 30L;
    private static final Long WHITEBOARD_ID = 600L;

    @Mock private WhiteboardRepository whiteboardRepository;
    @Mock private TeamMemberService teamMemberService;
    @Mock private TeamService teamService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private WhiteboardService whiteboardService;

    @BeforeEach
    void setUp() {
        whiteboardService =
                new WhiteboardService(
                        whiteboardRepository, teamMemberService, teamService, eventPublisher);
    }

    // ---------- 준비 ----------

    private Whiteboard givenWhiteboard() {
        Whiteboard whiteboard = Whiteboard.create(TEAM_ID, CREATOR_ID);
        ReflectionTestUtils.setField(whiteboard, "id", WHITEBOARD_ID);
        when(whiteboardRepository.findByIdAndDeletedAtIsNull(WHITEBOARD_ID))
                .thenReturn(Optional.of(whiteboard));
        return whiteboard;
    }

    /** 없거나 삭제된 보드: repository가 deleted_at IS NULL로 거르므로 둘 다 빈 값이다. */
    private void givenNoWhiteboard() {
        when(whiteboardRepository.findByIdAndDeletedAtIsNull(WHITEBOARD_ID))
                .thenReturn(Optional.empty());
    }

    private void givenSaveAssignsId() {
        when(whiteboardRepository.save(any(Whiteboard.class)))
                .thenAnswer(
                        invocation -> {
                            Whiteboard saved = invocation.getArgument(0);
                            ReflectionTestUtils.setField(saved, "id", WHITEBOARD_ID);
                            return saved;
                        });
    }

    private WhiteboardChangedEvent capturePublished() {
        ArgumentCaptor<WhiteboardChangedEvent> captor =
                ArgumentCaptor.forClass(WhiteboardChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return captor.getValue();
    }

    private static void assertErrorCode(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(expected);
    }

    // ---------- 만들기 (WB-01 ②③) ----------

    @Test
    @DisplayName("만들기: 29개면 「제목 없는 보드」로 만들고 CREATED를 발행한다. 팀 행을 잠근 뒤 센다")
    void 만들기_성공() {
        when(whiteboardRepository.countByTeamIdAndDeletedAtIsNull(TEAM_ID)).thenReturn(29L);
        when(whiteboardRepository.findDefaultTitles(TEAM_ID, Whiteboard.DEFAULT_TITLE))
                .thenReturn(List.of());
        givenSaveAssignsId();

        WhiteboardTitleResponse response = whiteboardService.create(CREATOR_ID, TEAM_ID);

        assertThat(response).isEqualTo(new WhiteboardTitleResponse(WHITEBOARD_ID, "제목 없는 보드"));
        ArgumentCaptor<Whiteboard> captor = ArgumentCaptor.forClass(Whiteboard.class);
        verify(whiteboardRepository).save(captor.capture());
        Whiteboard saved = captor.getValue();
        assertThat(saved.getTeamId()).isEqualTo(TEAM_ID);
        assertThat(saved.getCreatorId()).isEqualTo(CREATOR_ID);
        assertThat(saved.getLastEditorId()).isNull();
        assertThat(saved.isDeleted()).isFalse();
        assertThat(capturePublished())
                .isEqualTo(
                        new WhiteboardChangedEvent(
                                TEAM_ID, WHITEBOARD_ID, WhiteboardChangeType.CREATED, CREATOR_ID));

        // 잠금이 첫 SQL이어야 REPEATABLE READ 스냅숏이 잠금 뒤에 잡힌다 (WhiteboardMysqlTest 동시 생성)
        InOrder order = inOrder(teamMemberService, teamService, whiteboardRepository);
        order.verify(teamService).lockTeam(TEAM_ID);
        order.verify(teamMemberService).requireMember(TEAM_ID, CREATOR_ID);
        order.verify(whiteboardRepository).countByTeamIdAndDeletedAtIsNull(TEAM_ID);
        order.verify(whiteboardRepository).findDefaultTitles(TEAM_ID, Whiteboard.DEFAULT_TITLE);
        order.verify(whiteboardRepository).save(any());
    }

    @Test
    @DisplayName("만들기: 기존 기본 제목이 있으면 가장 큰 번호 + 1로 만든다")
    void 만들기_번호_증가() {
        when(whiteboardRepository.countByTeamIdAndDeletedAtIsNull(TEAM_ID)).thenReturn(2L);
        when(whiteboardRepository.findDefaultTitles(TEAM_ID, Whiteboard.DEFAULT_TITLE))
                .thenReturn(List.of("제목 없는 보드 (2)", "제목 없는 보드 (5)", "제목 없는 보드 (복사본)"));
        givenSaveAssignsId();

        WhiteboardTitleResponse response = whiteboardService.create(CREATOR_ID, TEAM_ID);

        assertThat(response).isEqualTo(new WhiteboardTitleResponse(WHITEBOARD_ID, "제목 없는 보드 (6)"));
    }

    @Test
    @DisplayName("만들기: 이미 30개면 409 WHITEBOARD_LIMIT, 저장·발행하지 않는다")
    void 만들기_30개면_409() {
        when(whiteboardRepository.countByTeamIdAndDeletedAtIsNull(TEAM_ID)).thenReturn(30L);

        assertErrorCode(
                () -> whiteboardService.create(CREATOR_ID, TEAM_ID), ErrorCode.WHITEBOARD_LIMIT);
        assertThat(ErrorCode.WHITEBOARD_LIMIT.getHttpStatus().value()).isEqualTo(409);
        verify(whiteboardRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("만들기: 팀원이 아니면 (잠근 뒤) 403 NOT_TEAM_MEMBER, 세거나 저장하지 않는다")
    void 만들기_팀원_아니면_403() {
        doThrow(new BusinessException(ErrorCode.NOT_TEAM_MEMBER))
                .when(teamMemberService)
                .requireMember(TEAM_ID, OTHER_ID);

        assertErrorCode(
                () -> whiteboardService.create(OTHER_ID, TEAM_ID), ErrorCode.NOT_TEAM_MEMBER);
        verify(teamService).lockTeam(TEAM_ID);
        verifyNoInteractions(whiteboardRepository, eventPublisher);
    }

    @Test
    @DisplayName("만들기: 없는 팀이면 팀원 확인 전에 404 RESOURCE_NOT_FOUND")
    void 만들기_없는_팀이면_404() {
        doThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND))
                .when(teamService)
                .lockTeam(TEAM_ID);

        assertErrorCode(
                () -> whiteboardService.create(CREATOR_ID, TEAM_ID), ErrorCode.RESOURCE_NOT_FOUND);
        verifyNoInteractions(teamMemberService, whiteboardRepository, eventPublisher);
    }

    // ---------- 제목 변경 (WB-01 ①) ----------

    @Test
    @DisplayName("제목 변경: 팀원 누구나, 앞뒤 공백을 지워 저장하고 UPDATED를 발행한다. 마지막 수정자는 바꾸지 않는다")
    void 제목변경_성공() {
        Whiteboard whiteboard = givenWhiteboard();

        WhiteboardTitleResponse response =
                whiteboardService.rename(OTHER_ID, WHITEBOARD_ID, "  아이디어 회의  ");

        assertThat(response).isEqualTo(new WhiteboardTitleResponse(WHITEBOARD_ID, "아이디어 회의"));
        assertThat(whiteboard.getTitle()).isEqualTo("아이디어 회의");
        // DB 명세서: last_editor_id는 동기화 서버가 저장할 때만 채운다
        assertThat(whiteboard.getLastEditorId()).isNull();
        verify(teamMemberService).requireMember(TEAM_ID, OTHER_ID);
        assertThat(capturePublished())
                .isEqualTo(
                        new WhiteboardChangedEvent(
                                TEAM_ID, WHITEBOARD_ID, WhiteboardChangeType.UPDATED, OTHER_ID));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    @DisplayName("제목 변경: null·빈 값·공백뿐이면 「제목 없는 보드」")
    void 제목변경_빈_값이면_기본_제목(String title) {
        Whiteboard whiteboard = givenWhiteboard();
        whiteboard.rename("기존 제목");

        WhiteboardTitleResponse response =
                whiteboardService.rename(CREATOR_ID, WHITEBOARD_ID, title);

        assertThat(whiteboard.getTitle()).isEqualTo("제목 없는 보드");
        assertThat(response.title()).isEqualTo("제목 없는 보드");
    }

    @Test
    @DisplayName("제목 변경: 50자는 되고 51자는 400 INVALID_INPUT (이모지는 1자로 센다)")
    void 제목변경_길이() {
        Whiteboard whiteboard = givenWhiteboard();
        String fifty = "😀".repeat(50);

        whiteboardService.rename(CREATOR_ID, WHITEBOARD_ID, fifty);
        assertThat(whiteboard.getTitle()).isEqualTo(fifty);

        assertErrorCode(
                () -> whiteboardService.rename(CREATOR_ID, WHITEBOARD_ID, "가".repeat(51)),
                ErrorCode.INVALID_INPUT);
        assertThat(whiteboard.getTitle()).isEqualTo(fifty);
    }

    @Test
    @DisplayName("제목 변경: 같은 제목이면 바꾸지 않고 발행하지 않지만, 응답에는 지금 제목을 준다")
    void 제목변경_같은_제목이면_발행_안_함() {
        givenWhiteboard();

        WhiteboardTitleResponse response =
                whiteboardService.rename(OTHER_ID, WHITEBOARD_ID, " 제목 없는 보드 ");

        assertThat(response.title()).isEqualTo("제목 없는 보드");
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("제목 변경: 없거나 삭제된 보드는 404 RESOURCE_NOT_FOUND (팀원 확인 전에)")
    void 제목변경_없는_보드_404() {
        givenNoWhiteboard();

        assertErrorCode(
                () -> whiteboardService.rename(CREATOR_ID, WHITEBOARD_ID, "제목"),
                ErrorCode.RESOURCE_NOT_FOUND);
        verifyNoInteractions(teamMemberService, eventPublisher);
    }

    @Test
    @DisplayName("제목 변경: 팀원이 아니면 403 NOT_TEAM_MEMBER")
    void 제목변경_팀원_아니면_403() {
        Whiteboard whiteboard = givenWhiteboard();
        doThrow(new BusinessException(ErrorCode.NOT_TEAM_MEMBER))
                .when(teamMemberService)
                .requireMember(TEAM_ID, OTHER_ID);

        assertErrorCode(
                () -> whiteboardService.rename(OTHER_ID, WHITEBOARD_ID, "제목"),
                ErrorCode.NOT_TEAM_MEMBER);
        assertThat(whiteboard.getTitle()).isEqualTo("제목 없는 보드");
    }

    // ---------- 삭제 (WB-01 ①) ----------

    @Test
    @DisplayName("삭제: 만든 사람은 지울 수 있고(팀장 여부를 읽지 않음) deleted_at을 기록하고 DELETED를 발행한다")
    void 삭제_만든_사람() {
        Whiteboard whiteboard = givenWhiteboard();

        whiteboardService.delete(CREATOR_ID, WHITEBOARD_ID);

        assertThat(whiteboard.isDeleted()).isTrue();
        verify(teamMemberService, never()).isLeader(anyLong(), anyLong());
        verify(whiteboardRepository, never()).delete(any());
        assertThat(capturePublished())
                .isEqualTo(
                        new WhiteboardChangedEvent(
                                TEAM_ID, WHITEBOARD_ID, WhiteboardChangeType.DELETED, CREATOR_ID));
    }

    @Test
    @DisplayName("삭제: 만든 사람이 아니어도 팀장은 지울 수 있다")
    void 삭제_팀장() {
        Whiteboard whiteboard = givenWhiteboard();
        when(teamMemberService.isLeader(TEAM_ID, LEADER_ID)).thenReturn(true);

        whiteboardService.delete(LEADER_ID, WHITEBOARD_ID);

        assertThat(whiteboard.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("삭제: 만든 사람도 팀장도 아닌 팀원은 403 NOT_WHITEBOARD_AUTHOR")
    void 삭제_다른_팀원_403() {
        Whiteboard whiteboard = givenWhiteboard();
        when(teamMemberService.isLeader(TEAM_ID, OTHER_ID)).thenReturn(false);

        assertErrorCode(
                () -> whiteboardService.delete(OTHER_ID, WHITEBOARD_ID),
                ErrorCode.NOT_WHITEBOARD_AUTHOR);
        assertThat(ErrorCode.NOT_WHITEBOARD_AUTHOR.getHttpStatus().value()).isEqualTo(403);
        assertThat(whiteboard.isDeleted()).isFalse();
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("삭제: 팀원이 아니면 만든 사람 확인 전에 403 NOT_TEAM_MEMBER")
    void 삭제_팀원_아니면_403() {
        givenWhiteboard();
        doThrow(new BusinessException(ErrorCode.NOT_TEAM_MEMBER))
                .when(teamMemberService)
                .requireMember(TEAM_ID, CREATOR_ID);

        assertErrorCode(
                () -> whiteboardService.delete(CREATOR_ID, WHITEBOARD_ID),
                ErrorCode.NOT_TEAM_MEMBER);
        verify(teamMemberService, never()).isLeader(anyLong(), anyLong());
    }

    @Test
    @DisplayName("삭제: 없거나 이미 삭제된 보드는 404 RESOURCE_NOT_FOUND")
    void 삭제_없는_보드_404() {
        givenNoWhiteboard();

        assertErrorCode(
                () -> whiteboardService.delete(CREATOR_ID, WHITEBOARD_ID),
                ErrorCode.RESOURCE_NOT_FOUND);
        verifyNoInteractions(teamMemberService, eventPublisher);
    }
}
