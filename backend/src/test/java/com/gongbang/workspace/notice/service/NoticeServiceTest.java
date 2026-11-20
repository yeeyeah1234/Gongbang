package com.gongbang.workspace.notice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.team.service.TeamMemberService;
import com.gongbang.workspace.notice.domain.Notice;
import com.gongbang.workspace.notice.domain.NoticeChangeType;
import com.gongbang.workspace.notice.domain.NoticeChangedEvent;
import com.gongbang.workspace.notice.domain.NoticeCreatedEvent;
import com.gongbang.workspace.notice.dto.NoticeCreateRequest;
import com.gongbang.workspace.notice.dto.NoticeUpdateRequest;
import com.gongbang.workspace.notice.repository.NoticeRepository;
import java.util.ArrayList;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

/** 권한·글자 수·고정 교체 순서·editedAt·이벤트를 본다. UNIQUE·CASCADE 같은 DB 동작은 NoticeMysqlTest에서 본다. */
@ExtendWith(MockitoExtension.class)
class NoticeServiceTest {

    private static final Long TEAM_ID = 1L;
    private static final Long LEADER_ID = 10L;
    private static final Long MEMBER_ID = 20L;
    private static final Long NEW_NOTICE_ID = 500L;
    private static final String PIN_DUPLICATE_MESSAGE =
            "Duplicate entry '1-1' for key 'notice.uk_notice_team_id_pinned_flag'";

    @Mock private NoticeRepository noticeRepository;
    @Mock private TeamMemberService teamMemberService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private NoticeService noticeService;

    @BeforeEach
    void setUp() {
        noticeService = new NoticeService(noticeRepository, teamMemberService, eventPublisher);
    }

    // ---------- 준비 ----------

    private static Notice notice(Long id, boolean pinned) {
        Notice notice = Notice.create(TEAM_ID, LEADER_ID, "제목", "내용", pinned);
        ReflectionTestUtils.setField(notice, "id", id);
        return notice;
    }

    private void givenNotice(Notice notice) {
        when(noticeRepository.findById(notice.getId())).thenReturn(Optional.of(notice));
    }

    private void givenNotLeader() {
        doThrow(new BusinessException(ErrorCode.NOT_TEAM_LEADER))
                .when(teamMemberService)
                .requireLeader(TEAM_ID, MEMBER_ID);
    }

    /** save가 넘겨받은 공지에 ID를 붙여 돌려주게 한다 (IDENTITY 흉내). */
    private void givenSaveAssignsId() {
        when(noticeRepository.save(any(Notice.class)))
                .thenAnswer(
                        invocation -> {
                            Notice saved = invocation.getArgument(0);
                            ReflectionTestUtils.setField(saved, "id", NEW_NOTICE_ID);
                            return saved;
                        });
    }

    private List<Object> publishedEvents() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(captor.capture());
        return captor.getAllValues();
    }

    private void verifyNoEvents() {
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    private static NoticeChangedEvent changed(Long noticeId, NoticeChangeType type) {
        return new NoticeChangedEvent(TEAM_ID, noticeId, type);
    }

    private static void assertErrorCode(Runnable call, ErrorCode errorCode) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(errorCode);
    }

    // ---------- 작성 (NTC-01) ----------

    @Test
    @DisplayName("작성: 팀장이 아니면 403 NOT_TEAM_LEADER, 저장·이벤트 없음")
    void 작성_팀장이_아니면_403() {
        givenNotLeader();

        assertErrorCode(
                () ->
                        noticeService.create(
                                MEMBER_ID, TEAM_ID, new NoticeCreateRequest("제목", "내용", false)),
                ErrorCode.NOT_TEAM_LEADER);

        verify(noticeRepository, never()).save(any());
        verifyNoEvents();
    }

    @Test
    @DisplayName("작성: 팀원이 아니어도 requireLeader가 403 NOT_TEAM_MEMBER를 내면 그대로 전달한다")
    void 작성_팀원이_아니면_403() {
        doThrow(new BusinessException(ErrorCode.NOT_TEAM_MEMBER))
                .when(teamMemberService)
                .requireLeader(TEAM_ID, MEMBER_ID);

        assertErrorCode(
                () ->
                        noticeService.create(
                                MEMBER_ID, TEAM_ID, new NoticeCreateRequest("제목", "내용", false)),
                ErrorCode.NOT_TEAM_MEMBER);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\n\t"})
    @DisplayName("작성: 제목이 비었거나 공백뿐이면 400")
    void 작성_제목_공백이면_400(String title) {
        assertErrorCode(
                () ->
                        noticeService.create(
                                LEADER_ID, TEAM_ID, new NoticeCreateRequest(title, "내용", false)),
                ErrorCode.INVALID_INPUT);
        verify(noticeRepository, never()).save(any());
    }

    @Test
    @DisplayName("작성: 제목 51자, 내용 0자·2001자는 400")
    void 작성_글자_수_초과는_400() {
        assertErrorCode(
                () ->
                        noticeService.create(
                                LEADER_ID,
                                TEAM_ID,
                                new NoticeCreateRequest("가".repeat(51), "내용", false)),
                ErrorCode.INVALID_INPUT);
        assertErrorCode(
                () ->
                        noticeService.create(
                                LEADER_ID, TEAM_ID, new NoticeCreateRequest("제목", "", false)),
                ErrorCode.INVALID_INPUT);
        assertErrorCode(
                () ->
                        noticeService.create(
                                LEADER_ID,
                                TEAM_ID,
                                new NoticeCreateRequest("제목", "가".repeat(2001), false)),
                ErrorCode.INVALID_INPUT);
        verify(noticeRepository, never()).save(any());
    }

    @Test
    @DisplayName("작성: 앞뒤 공백을 빼고 저장한다. 제목 50자·내용 2000자(이모지 포함, 문자 단위)는 통과")
    void 작성_경계값과_공백_제거() {
        givenSaveAssignsId();
        String title = "😀".repeat(50);
        String content = "😀".repeat(2000);

        noticeService.create(
                LEADER_ID,
                TEAM_ID,
                new NoticeCreateRequest("  " + title + "  ", "\n" + content + " ", null));

        ArgumentCaptor<Notice> captor = ArgumentCaptor.forClass(Notice.class);
        verify(noticeRepository).save(captor.capture());
        Notice saved = captor.getValue();
        assertThat(saved.getTitle()).isEqualTo(title);
        assertThat(saved.getContent()).isEqualTo(content);
        assertThat(saved.getTeamId()).isEqualTo(TEAM_ID);
        assertThat(saved.getAuthorId()).isEqualTo(LEADER_ID);
        assertThat(saved.isPinned()).isFalse();
        assertThat(saved.getEditedAt()).isNull();
    }

    @Test
    @DisplayName("작성: 고정 없이 쓰면 기존 고정은 건드리지 않고 NoticeCreatedEvent·CREATED를 발행한다")
    void 작성_이벤트() {
        givenSaveAssignsId();

        Long id =
                noticeService.create(
                        LEADER_ID, TEAM_ID, new NoticeCreateRequest("제목", "내용", false));

        assertThat(id).isEqualTo(NEW_NOTICE_ID);
        verify(noticeRepository, never()).findByTeamIdAndPinnedTrue(any());
        verify(noticeRepository, never()).flush();
        assertThat(publishedEvents())
                .containsExactly(
                        new NoticeCreatedEvent(TEAM_ID, NEW_NOTICE_ID, LEADER_ID),
                        changed(NEW_NOTICE_ID, NoticeChangeType.CREATED));
    }

    @Test
    @DisplayName("작성(고정): 기존 고정 해제 → flush → 새 공지 save(고정 상태) → flush 순서, 기존 공지에 UNPINNED")
    void 작성_고정_교체_순서() {
        Notice old = notice(7L, true);
        when(noticeRepository.findByTeamIdAndPinnedTrue(TEAM_ID)).thenReturn(Optional.of(old));
        List<Boolean> oldPinnedAtSave = new ArrayList<>();
        when(noticeRepository.save(any(Notice.class)))
                .thenAnswer(
                        invocation -> {
                            oldPinnedAtSave.add(old.isPinned());
                            Notice saved = invocation.getArgument(0);
                            ReflectionTestUtils.setField(saved, "id", NEW_NOTICE_ID);
                            return saved;
                        });

        noticeService.create(LEADER_ID, TEAM_ID, new NoticeCreateRequest("제목", "내용", true));

        InOrder order = inOrder(noticeRepository);
        order.verify(noticeRepository).findByTeamIdAndPinnedTrue(TEAM_ID);
        order.verify(noticeRepository).flush();
        order.verify(noticeRepository).save(any(Notice.class));
        order.verify(noticeRepository).flush();
        assertThat(oldPinnedAtSave).containsExactly(false);

        ArgumentCaptor<Notice> captor = ArgumentCaptor.forClass(Notice.class);
        verify(noticeRepository).save(captor.capture());
        assertThat(captor.getValue().isPinned()).isTrue();
        assertThat(old.isPinned()).isFalse();
        assertThat(old.getEditedAt()).isNull();
        assertThat(publishedEvents())
                .containsExactly(
                        changed(7L, NoticeChangeType.UNPINNED),
                        new NoticeCreatedEvent(TEAM_ID, NEW_NOTICE_ID, LEADER_ID),
                        changed(NEW_NOTICE_ID, NoticeChangeType.CREATED));
    }

    @Test
    @DisplayName("작성(고정): 기존 고정이 없으면 해제 flush 없이 save → flush")
    void 작성_고정_기존_없음() {
        when(noticeRepository.findByTeamIdAndPinnedTrue(TEAM_ID)).thenReturn(Optional.empty());
        givenSaveAssignsId();

        noticeService.create(LEADER_ID, TEAM_ID, new NoticeCreateRequest("제목", "내용", true));

        InOrder order = inOrder(noticeRepository);
        order.verify(noticeRepository).save(any(Notice.class));
        order.verify(noticeRepository).flush();
        assertThat(publishedEvents())
                .containsExactly(
                        new NoticeCreatedEvent(TEAM_ID, NEW_NOTICE_ID, LEADER_ID),
                        changed(NEW_NOTICE_ID, NoticeChangeType.CREATED));
    }

    @Test
    @DisplayName("작성(고정): 동시 요청으로 UNIQUE에 걸리면 409 NOTICE_PIN_CONFLICT, 이벤트 없음")
    void 작성_고정_UNIQUE_위반은_409() {
        when(noticeRepository.findByTeamIdAndPinnedTrue(TEAM_ID)).thenReturn(Optional.empty());
        when(noticeRepository.save(any(Notice.class)))
                .thenThrow(new DataIntegrityViolationException(PIN_DUPLICATE_MESSAGE));

        assertErrorCode(
                () ->
                        noticeService.create(
                                LEADER_ID, TEAM_ID, new NoticeCreateRequest("제목", "내용", true)),
                ErrorCode.NOTICE_PIN_CONFLICT);
        verifyNoEvents();
    }

    @Test
    @DisplayName("작성(고정): 다른 제약 위반은 409로 바꾸지 않고 그대로 던진다")
    void 작성_다른_제약_위반은_그대로() {
        when(noticeRepository.findByTeamIdAndPinnedTrue(TEAM_ID)).thenReturn(Optional.empty());
        when(noticeRepository.save(any(Notice.class)))
                .thenThrow(new DataIntegrityViolationException("fk_notice_author"));

        assertThatThrownBy(
                        () ->
                                noticeService.create(
                                        LEADER_ID,
                                        TEAM_ID,
                                        new NoticeCreateRequest("제목", "내용", true)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------- 수정 (NTC-04) ----------

    @Test
    @DisplayName("수정: 제목·내용이 바뀌면 공백을 빼고 고치고 editedAt을 채우고 UPDATED를 발행한다")
    void 수정_editedAt_갱신() {
        Notice notice = notice(3L, false);
        givenNotice(notice);

        Long id = noticeService.update(LEADER_ID, 3L, new NoticeUpdateRequest(" 새 제목 ", " 새 내용\n"));

        assertThat(id).isEqualTo(3L);
        assertThat(notice.getTitle()).isEqualTo("새 제목");
        assertThat(notice.getContent()).isEqualTo("새 내용");
        assertThat(notice.getEditedAt()).isNotNull();
        assertThat(notice.isEdited()).isTrue();
        assertThat(publishedEvents()).containsExactly(changed(3L, NoticeChangeType.UPDATED));
    }

    @Test
    @DisplayName("수정: 공백을 뺀 값이 기존과 같으면 editedAt·이벤트 없음")
    void 수정_같은_값이면_그대로() {
        Notice notice = notice(3L, false);
        givenNotice(notice);

        noticeService.update(LEADER_ID, 3L, new NoticeUpdateRequest(" 제목", "내용 "));

        assertThat(notice.getEditedAt()).isNull();
        verifyNoEvents();
    }

    @Test
    @DisplayName("수정: 공지의 팀 기준으로 팀장이 아니면 403, 내용 그대로")
    void 수정_팀장이_아니면_403() {
        Notice notice = notice(3L, false);
        givenNotice(notice);
        givenNotLeader();

        assertErrorCode(
                () -> noticeService.update(MEMBER_ID, 3L, new NoticeUpdateRequest("새 제목", "새 내용")),
                ErrorCode.NOT_TEAM_LEADER);

        assertThat(notice.getTitle()).isEqualTo("제목");
        assertThat(notice.getEditedAt()).isNull();
        verifyNoEvents();
    }

    @Test
    @DisplayName("수정: 글자 수가 맞지 않으면 400")
    void 수정_글자_수_검증() {
        givenNotice(notice(3L, false));

        assertErrorCode(
                () ->
                        noticeService.update(
                                LEADER_ID, 3L, new NoticeUpdateRequest("가".repeat(51), "내용")),
                ErrorCode.INVALID_INPUT);
        assertErrorCode(
                () -> noticeService.update(LEADER_ID, 3L, new NoticeUpdateRequest("제목", " ")),
                ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("없는 공지면 수정·삭제·고정·해제 모두 404 RESOURCE_NOT_FOUND")
    void 없는_공지는_404() {
        when(noticeRepository.findById(99L)).thenReturn(Optional.empty());

        assertErrorCode(
                () -> noticeService.update(LEADER_ID, 99L, new NoticeUpdateRequest("제목", "내용")),
                ErrorCode.RESOURCE_NOT_FOUND);
        assertErrorCode(() -> noticeService.delete(LEADER_ID, 99L), ErrorCode.RESOURCE_NOT_FOUND);
        assertErrorCode(() -> noticeService.pin(LEADER_ID, 99L), ErrorCode.RESOURCE_NOT_FOUND);
        assertErrorCode(() -> noticeService.unpin(LEADER_ID, 99L), ErrorCode.RESOURCE_NOT_FOUND);
        verify(teamMemberService, never()).requireLeader(any(), any());
    }

    // ---------- 삭제 (NTC-03) ----------

    @Test
    @DisplayName("삭제: 행을 지우고 DELETED를 발행한다. 고정 공지여도 다른 공지를 고정하지 않는다")
    void 삭제() {
        Notice notice = notice(3L, true);
        givenNotice(notice);

        noticeService.delete(LEADER_ID, 3L);

        verify(noticeRepository).delete(notice);
        verify(noticeRepository, never()).findByTeamIdAndPinnedTrue(any());
        assertThat(publishedEvents()).containsExactly(changed(3L, NoticeChangeType.DELETED));
    }

    @Test
    @DisplayName("삭제: 팀장이 아니면 403, 지우지 않는다")
    void 삭제_팀장이_아니면_403() {
        givenNotice(notice(3L, false));
        givenNotLeader();

        assertErrorCode(() -> noticeService.delete(MEMBER_ID, 3L), ErrorCode.NOT_TEAM_LEADER);

        verify(noticeRepository, never()).delete(any());
        verifyNoEvents();
    }

    // ---------- 고정·해제 (NTC-05) ----------

    @Test
    @DisplayName("고정: 기존 고정 해제 → flush → 새 공지 고정 → flush, editedAt은 그대로, UNPINNED·PINNED 발행")
    void 고정_교체() {
        Notice old = notice(7L, true);
        Notice target = notice(8L, false);
        givenNotice(target);
        when(noticeRepository.findByTeamIdAndPinnedTrue(TEAM_ID)).thenReturn(Optional.of(old));
        // flush마다 (기존 고정, 대상 고정) 상태를 남긴다
        List<List<Boolean>> pinnedAtFlush = new ArrayList<>();
        doAnswer(
                        invocation -> {
                            pinnedAtFlush.add(List.of(old.isPinned(), target.isPinned()));
                            return null;
                        })
                .when(noticeRepository)
                .flush();

        noticeService.pin(LEADER_ID, 8L);

        InOrder order = inOrder(noticeRepository);
        order.verify(noticeRepository).findByTeamIdAndPinnedTrue(TEAM_ID);
        order.verify(noticeRepository, times(2)).flush();
        // 첫 flush: 기존만 풀린 상태, 둘째 flush: 대상이 고정된 상태
        assertThat(pinnedAtFlush).containsExactly(List.of(false, false), List.of(false, true));
        assertThat(old.isPinned()).isFalse();
        assertThat(target.isPinned()).isTrue();
        assertThat(old.getEditedAt()).isNull();
        assertThat(target.getEditedAt()).isNull();
        assertThat(publishedEvents())
                .containsExactly(
                        changed(7L, NoticeChangeType.UNPINNED),
                        changed(8L, NoticeChangeType.PINNED));
    }

    @Test
    @DisplayName("고정: 새 공지 고정 flush가 UNIQUE에 걸리면 409, 이벤트 없음")
    void 고정_UNIQUE_위반은_409() {
        Notice target = notice(8L, false);
        givenNotice(target);
        when(noticeRepository.findByTeamIdAndPinnedTrue(TEAM_ID)).thenReturn(Optional.empty());
        doThrow(new DataIntegrityViolationException(PIN_DUPLICATE_MESSAGE))
                .when(noticeRepository)
                .flush();

        assertErrorCode(() -> noticeService.pin(LEADER_ID, 8L), ErrorCode.NOTICE_PIN_CONFLICT);
        verifyNoEvents();
    }

    @Test
    @DisplayName("고정: 이미 고정된 공지면 아무것도 하지 않고 성공")
    void 고정_이미_고정이면_그대로() {
        givenNotice(notice(8L, true));

        noticeService.pin(LEADER_ID, 8L);

        verify(noticeRepository, never()).findByTeamIdAndPinnedTrue(any());
        verify(noticeRepository, never()).flush();
        verifyNoEvents();
    }

    @Test
    @DisplayName("고정: 팀장이 아니면 403, 고정하지 않는다")
    void 고정_팀장이_아니면_403() {
        Notice target = notice(8L, false);
        givenNotice(target);
        givenNotLeader();

        assertErrorCode(() -> noticeService.pin(MEMBER_ID, 8L), ErrorCode.NOT_TEAM_LEADER);

        assertThat(target.isPinned()).isFalse();
        verifyNoEvents();
    }

    @Test
    @DisplayName("해제: 고정을 풀고 UNPINNED를 발행한다. editedAt은 그대로")
    void 해제() {
        Notice notice = notice(8L, true);
        givenNotice(notice);

        noticeService.unpin(LEADER_ID, 8L);

        assertThat(notice.isPinned()).isFalse();
        assertThat(notice.getEditedAt()).isNull();
        assertThat(publishedEvents()).containsExactly(changed(8L, NoticeChangeType.UNPINNED));
    }

    @Test
    @DisplayName("해제: 이미 풀려 있으면 아무것도 하지 않고 성공")
    void 해제_이미_해제면_그대로() {
        givenNotice(notice(8L, false));

        noticeService.unpin(LEADER_ID, 8L);

        verifyNoEvents();
    }

    @Test
    @DisplayName("해제: 팀장이 아니면 403")
    void 해제_팀장이_아니면_403() {
        Notice notice = notice(8L, true);
        givenNotice(notice);
        givenNotLeader();

        assertErrorCode(() -> noticeService.unpin(MEMBER_ID, 8L), ErrorCode.NOT_TEAM_LEADER);

        assertThat(notice.isPinned()).isTrue();
    }
}
