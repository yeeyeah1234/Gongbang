package com.gongbang.workspace.notice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gongbang.global.common.PageResponse;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.service.MemberService;
import com.gongbang.team.service.TeamMemberService;
import com.gongbang.workspace.notice.domain.Notice;
import com.gongbang.workspace.notice.dto.NoticeAuthor;
import com.gongbang.workspace.notice.dto.NoticeDetailResponse;
import com.gongbang.workspace.notice.dto.NoticeListItemResponse;
import com.gongbang.workspace.notice.repository.NoticeReadRepository;
import com.gongbang.workspace.notice.repository.NoticeRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

/** 팀원 권한, 목록 항목 값(안 읽음·수정됨·미리보기·작성자), 읽음 기록 호출을 본다. 정렬·쿼리 의미는 NoticeMysqlTest에서 본다. */
@ExtendWith(MockitoExtension.class)
class NoticeQueryServiceTest {

    private static final Long TEAM_ID = 1L;
    private static final Long LEADER_ID = 10L;
    private static final Long ME = 20L;
    private static final Long OUTSIDER = 30L;

    @Mock private NoticeRepository noticeRepository;
    @Mock private NoticeReadRepository noticeReadRepository;
    @Mock private TeamMemberService teamMemberService;
    @Mock private MemberService memberService;

    private NoticeQueryService noticeQueryService;

    @BeforeEach
    void setUp() {
        noticeQueryService =
                new NoticeQueryService(
                        noticeRepository, noticeReadRepository, teamMemberService, memberService);
    }

    private static Notice notice(Long id, Long authorId, String content, boolean pinned) {
        Notice notice = Notice.create(TEAM_ID, authorId, "제목" + id, content, pinned);
        ReflectionTestUtils.setField(notice, "id", id);
        return notice;
    }

    private void givenNotMember(Long memberId) {
        doThrow(new BusinessException(ErrorCode.NOT_TEAM_MEMBER))
                .when(teamMemberService)
                .requireMember(TEAM_ID, memberId);
    }

    private static void assertErrorCode(Runnable call, ErrorCode errorCode) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(errorCode);
    }

    // ---------- 권한 ----------

    @Test
    @DisplayName("팀원이 아니면 목록·배너·안 읽음 개수 모두 403 NOT_TEAM_MEMBER")
    void 팀원이_아니면_403() {
        givenNotMember(OUTSIDER);

        assertErrorCode(
                () -> noticeQueryService.getNotices(OUTSIDER, TEAM_ID, 0, 10),
                ErrorCode.NOT_TEAM_MEMBER);
        assertErrorCode(
                () -> noticeQueryService.getPinned(OUTSIDER, TEAM_ID), ErrorCode.NOT_TEAM_MEMBER);
        assertErrorCode(
                () -> noticeQueryService.getUnreadCount(OUTSIDER, TEAM_ID),
                ErrorCode.NOT_TEAM_MEMBER);
        verify(noticeRepository, never()).findByTeamId(anyLong(), any());
        verify(noticeRepository, never()).countUnread(anyLong(), anyLong());
    }

    @Test
    @DisplayName("상세: 공지 팀의 팀원이 아니면 403, 읽음 기록을 남기지 않는다")
    void 상세_팀원이_아니면_403() {
        when(noticeRepository.findById(5L))
                .thenReturn(Optional.of(notice(5L, LEADER_ID, "내용", false)));
        givenNotMember(OUTSIDER);

        assertErrorCode(
                () -> noticeQueryService.getNotice(OUTSIDER, 5L), ErrorCode.NOT_TEAM_MEMBER);
        verify(noticeReadRepository, never()).markRead(any(), any());
    }

    @Test
    @DisplayName("상세: 없는 공지면 404 RESOURCE_NOT_FOUND")
    void 상세_없는_공지는_404() {
        when(noticeRepository.findById(99L)).thenReturn(Optional.empty());

        assertErrorCode(() -> noticeQueryService.getNotice(ME, 99L), ErrorCode.RESOURCE_NOT_FOUND);
        verify(noticeReadRepository, never()).markRead(any(), any());
    }

    // ---------- 목록 (NTC-01) ----------

    @ParameterizedTest
    @CsvSource({"-1, 10", "0, 0", "0, 51"})
    @DisplayName("목록: page < 0, size가 1~50 밖이면 400")
    void 목록_페이지_값_검증(int page, int size) {
        assertErrorCode(
                () -> noticeQueryService.getNotices(ME, TEAM_ID, page, size),
                ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("목록: 고정 → 최신순 정렬로 조회하고 안 읽음·수정됨·작성자를 채운다")
    void 목록_항목_값() {
        Notice pinnedUnread = notice(3L, LEADER_ID, "고정 공지", true);
        Notice read = notice(2L, LEADER_ID, "읽은 공지", false);
        ReflectionTestUtils.setField(read, "editedAt", LocalDateTime.of(2026, 11, 20, 9, 0));
        Notice mine = notice(1L, ME, "내가 쓴 공지", false);
        Notice noAuthor = notice(4L, null, "작성자 없는 공지", false);
        List<Notice> content = List.of(pinnedUnread, read, mine, noAuthor);
        when(noticeRepository.findByTeamId(eq(TEAM_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(content, PageRequest.of(0, 10), 4));
        when(noticeReadRepository.findReadNoticeIds(ME, List.of(3L, 2L, 1L, 4L)))
                .thenReturn(List.of(2L));
        when(memberService.displayName(LEADER_ID)).thenReturn("팀장");
        when(memberService.displayName(ME)).thenReturn("나");

        PageResponse<NoticeListItemResponse> result =
                noticeQueryService.getNotices(ME, TEAM_ID, 0, 10);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(noticeRepository).findByTeamId(eq(TEAM_ID), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
        assertThat(pageable.getValue().getSort()).isEqualTo(NoticeQueryService.PINNED_FIRST_LATEST);

        assertThat(result.totalCount()).isEqualTo(4);
        assertThat(result.hasNext()).isFalse();
        List<NoticeListItemResponse> items = result.items();
        assertThat(items)
                .extracting(NoticeListItemResponse::noticeId)
                .containsExactly(3L, 2L, 1L, 4L);
        assertThat(items)
                .extracting(NoticeListItemResponse::isUnread)
                .containsExactly(true, false, false, true);
        assertThat(items)
                .extracting(NoticeListItemResponse::isEdited)
                .containsExactly(false, true, false, false);
        assertThat(items)
                .extracting(NoticeListItemResponse::isPinned)
                .containsExactly(true, false, false, false);
        assertThat(items)
                .extracting(NoticeListItemResponse::author)
                .containsExactly(
                        new NoticeAuthor(LEADER_ID, "팀장"),
                        new NoticeAuthor(LEADER_ID, "팀장"),
                        new NoticeAuthor(ME, "나"),
                        new NoticeAuthor(null, "탈퇴한 사용자"));
        // 같은 작성자는 한 번만 읽는다
        verify(memberService).displayName(LEADER_ID);
    }

    @Test
    @DisplayName("목록 미리보기: 첫 줄만, 최대 100자(이모지도 한 글자로 센다)")
    void 목록_미리보기() {
        Notice multiLine = notice(1L, LEADER_ID, "  첫 줄 \n둘째 줄\n셋째 줄", false);
        Notice longLine = notice(2L, LEADER_ID, "😀".repeat(150), false);
        when(noticeRepository.findByTeamId(eq(TEAM_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(multiLine, longLine), PageRequest.of(0, 10), 2));
        when(noticeReadRepository.findReadNoticeIds(any(), any())).thenReturn(List.of());
        when(memberService.displayName(LEADER_ID)).thenReturn("팀장");

        List<NoticeListItemResponse> items =
                noticeQueryService.getNotices(ME, TEAM_ID, 0, 10).items();

        assertThat(items.get(0).preview()).isEqualTo("첫 줄");
        assertThat(items.get(1).preview()).isEqualTo("😀".repeat(100));
    }

    @Test
    @DisplayName("목록: 공지가 없으면 읽음 기록을 조회하지 않는다")
    void 목록_빈_페이지() {
        when(noticeRepository.findByTeamId(eq(TEAM_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        PageResponse<NoticeListItemResponse> result =
                noticeQueryService.getNotices(ME, TEAM_ID, 0, 10);

        assertThat(result.items()).isEmpty();
        verify(noticeReadRepository, never()).findReadNoticeIds(any(), any());
    }

    // ---------- 상세 (NTC-02) ----------

    @Test
    @DisplayName("상세: 공지 팀으로 팀원을 확인하고 읽음 기록을 남긴다")
    void 상세_읽음_기록() {
        Notice notice = notice(5L, LEADER_ID, "내용", true);
        ReflectionTestUtils.setField(notice, "editedAt", LocalDateTime.of(2026, 11, 20, 9, 0));
        when(noticeRepository.findById(5L)).thenReturn(Optional.of(notice));
        when(memberService.displayName(LEADER_ID)).thenReturn("팀장");

        NoticeDetailResponse detail = noticeQueryService.getNotice(ME, 5L);

        verify(teamMemberService).requireMember(TEAM_ID, ME);
        verify(noticeReadRepository).markRead(5L, ME);
        assertThat(detail.noticeId()).isEqualTo(5L);
        assertThat(detail.teamId()).isEqualTo(TEAM_ID);
        assertThat(detail.content()).isEqualTo("내용");
        assertThat(detail.author()).isEqualTo(new NoticeAuthor(LEADER_ID, "팀장"));
        assertThat(detail.isPinned()).isTrue();
        assertThat(detail.isEdited()).isTrue();
        assertThat(detail.editedAt()).isEqualTo(LocalDateTime.of(2026, 11, 20, 9, 0));
    }

    // ---------- 배너·개수 ----------

    @Test
    @DisplayName("배너: 고정 공지가 없으면 null, 있으면 상세 값. 읽음 기록은 남기지 않는다")
    void 배너() {
        when(noticeRepository.findByTeamIdAndPinnedTrue(TEAM_ID)).thenReturn(Optional.empty());
        assertThat(noticeQueryService.getPinned(ME, TEAM_ID)).isNull();

        when(noticeRepository.findByTeamIdAndPinnedTrue(TEAM_ID))
                .thenReturn(Optional.of(notice(5L, LEADER_ID, "고정", true)));
        when(memberService.displayName(LEADER_ID)).thenReturn("팀장");
        NoticeDetailResponse pinned = noticeQueryService.getPinned(ME, TEAM_ID);

        assertThat(pinned.noticeId()).isEqualTo(5L);
        assertThat(pinned.isPinned()).isTrue();
        verify(noticeReadRepository, never()).markRead(any(), any());
    }

    @Test
    @DisplayName("안 읽음 개수: 리포지토리 count를 그대로 준다")
    void 안_읽음_개수() {
        when(noticeRepository.countUnread(TEAM_ID, ME)).thenReturn(3L);

        assertThat(noticeQueryService.getUnreadCount(ME, TEAM_ID).count()).isEqualTo(3L);
    }
}
