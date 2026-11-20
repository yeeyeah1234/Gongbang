package com.gongbang.workspace.notice.service;

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
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 팀 공지 작성·수정·삭제·고정 (NTC-01, NTC-03~05). 모두 팀장만 한다 (403 NOT_TEAM_LEADER). 조회는 {@link
 * NoticeQueryService}.
 *
 * <p>공지 ID로 들어오는 요청은 공지를 먼저 찾고(없으면 404 RESOURCE_NOT_FOUND) 그 공지의 팀으로 권한을 본다.
 *
 * <p>고정 교체는 같은 트랜잭션에서 「기존 고정 해제 → flush → 새 공지 고정 → flush」 순서다. 해제를 먼저 DB에 보내야 UNIQUE(team_id,
 * pinned_flag)에 걸리지 않는다. 그래도 동시 요청으로 UNIQUE에 걸리면 409 NOTICE_PIN_CONFLICT로 바꾼다. 커밋 때가 아니라 이 안에서
 * flush해야 예외를 잡아 바꿀 수 있다.
 *
 * <p>이벤트는 트랜잭션 안에서 발행한다. 받는 쪽은 AFTER_COMMIT으로 받는다 ({@link NoticeCreatedEvent}).
 */
@Service
@RequiredArgsConstructor
public class NoticeService {

    /** V17 UNIQUE 제약 이름. 이 제약 위반만 409로 바꾼다. */
    static final String PINNED_UNIQUE_CONSTRAINT = "uk_notice_team_id_pinned_flag";

    private final NoticeRepository noticeRepository;
    private final TeamMemberService teamMemberService;
    private final ApplicationEventPublisher eventPublisher;

    /** NTC-01 작성. pinned면 기존 고정 공지를 풀고 새 공지를 고정 상태로 넣는다. 새 공지 ID를 돌려준다. */
    @Transactional
    public Long create(Long memberId, Long teamId, NoticeCreateRequest request) {
        teamMemberService.requireLeader(teamId, memberId);
        String title = requireText(request.title(), Notice.TITLE_MAX_LENGTH);
        String content = requireText(request.content(), Notice.CONTENT_MAX_LENGTH);
        boolean pinned = request.pinnedOrFalse();

        Optional<Notice> unpinned = pinned ? unpinCurrent(teamId) : Optional.empty();
        Notice notice =
                pinned
                        ? saveAndFlushPinned(Notice.create(teamId, memberId, title, content, true))
                        : noticeRepository.save(
                                Notice.create(teamId, memberId, title, content, false));

        unpinned.ifPresent(old -> publishChanged(old, NoticeChangeType.UNPINNED));
        eventPublisher.publishEvent(new NoticeCreatedEvent(teamId, notice.getId(), memberId));
        publishChanged(notice, NoticeChangeType.CREATED);
        return notice.getId();
    }

    /** NTC-04 수정. 제목·내용이 실제로 바뀌었을 때만 editedAt을 채우고 UPDATED를 발행한다. 알림은 없다. */
    @Transactional
    public Long update(Long memberId, Long noticeId, NoticeUpdateRequest request) {
        Notice notice = findNotice(noticeId);
        teamMemberService.requireLeader(notice.getTeamId(), memberId);
        String title = requireText(request.title(), Notice.TITLE_MAX_LENGTH);
        String content = requireText(request.content(), Notice.CONTENT_MAX_LENGTH);

        if (notice.edit(title, content, LocalDateTime.now())) {
            publishChanged(notice, NoticeChangeType.UPDATED);
        }
        return notice.getId();
    }

    /** NTC-03 삭제. 행을 지운다 (notice_read는 CASCADE). 고정 공지였어도 다른 공지를 자동으로 고정하지 않는다. */
    @Transactional
    public void delete(Long memberId, Long noticeId) {
        Notice notice = findNotice(noticeId);
        teamMemberService.requireLeader(notice.getTeamId(), memberId);
        noticeRepository.delete(notice);
        publishChanged(notice, NoticeChangeType.DELETED);
    }

    /** NTC-05 고정. 이미 고정돼 있으면 아무것도 하지 않고 성공. */
    @Transactional
    public void pin(Long memberId, Long noticeId) {
        Notice notice = findNotice(noticeId);
        teamMemberService.requireLeader(notice.getTeamId(), memberId);
        if (notice.isPinned()) {
            return;
        }

        Optional<Notice> unpinned = unpinCurrent(notice.getTeamId());
        notice.pin();
        flushPinned();

        unpinned.ifPresent(old -> publishChanged(old, NoticeChangeType.UNPINNED));
        publishChanged(notice, NoticeChangeType.PINNED);
    }

    /** NTC-05 고정 해제. 이미 풀려 있으면 아무것도 하지 않고 성공. */
    @Transactional
    public void unpin(Long memberId, Long noticeId) {
        Notice notice = findNotice(noticeId);
        teamMemberService.requireLeader(notice.getTeamId(), memberId);
        if (!notice.isPinned()) {
            return;
        }
        notice.unpin();
        publishChanged(notice, NoticeChangeType.UNPINNED);
    }

    // ---------- 고정 교체 ----------

    /** 팀의 지금 고정 공지를 풀고 DB에 바로 보낸다. 푼 공지를 돌려준다 (없으면 빈 값). */
    private Optional<Notice> unpinCurrent(Long teamId) {
        Optional<Notice> current = noticeRepository.findByTeamIdAndPinnedTrue(teamId);
        current.ifPresent(
                old -> {
                    old.unpin();
                    noticeRepository.flush();
                });
        return current;
    }

    /** 고정 상태의 새 공지를 넣는다. IDENTITY라 save에서 바로 INSERT되므로 save도 409 변환 범위에 넣는다. */
    private Notice saveAndFlushPinned(Notice notice) {
        try {
            Notice saved = noticeRepository.save(notice);
            noticeRepository.flush();
            return saved;
        } catch (DataIntegrityViolationException e) {
            throw translatePinConflict(e);
        }
    }

    private void flushPinned() {
        try {
            noticeRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw translatePinConflict(e);
        }
    }

    /** 팀당 고정 1개 UNIQUE 위반이면 409, 다른 제약 위반이면 그대로 (500으로 드러나게). */
    private static RuntimeException translatePinConflict(DataIntegrityViolationException e) {
        String message = NestedExceptionUtils.getMostSpecificCause(e).getMessage();
        if (message != null && message.contains(PINNED_UNIQUE_CONSTRAINT)) {
            return new BusinessException(ErrorCode.NOTICE_PIN_CONFLICT);
        }
        return e;
    }

    // ---------- 공통 ----------

    private Notice findNotice(Long noticeId) {
        return noticeRepository
                .findById(noticeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private void publishChanged(Notice notice, NoticeChangeType type) {
        eventPublisher.publishEvent(
                new NoticeChangedEvent(notice.getTeamId(), notice.getId(), type));
    }

    /**
     * 앞뒤 공백을 뺀 값이 1자 이상, max자 이하인지 본다. 글자 수는 DB(utf8mb4)처럼 문자 단위로 센다 ({@code
     * PostCommandService.requireText}와 같은 규칙).
     */
    static String requireText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        String stripped = value.strip();
        if (stripped.codePointCount(0, stripped.length()) > maxLength) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return stripped;
    }
}
