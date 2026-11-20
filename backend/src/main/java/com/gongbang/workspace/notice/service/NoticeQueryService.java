package com.gongbang.workspace.notice.service;

import com.gongbang.global.common.PageResponse;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.service.MemberService;
import com.gongbang.team.service.TeamMemberService;
import com.gongbang.workspace.notice.domain.Notice;
import com.gongbang.workspace.notice.dto.NoticeAuthor;
import com.gongbang.workspace.notice.dto.NoticeDetailResponse;
import com.gongbang.workspace.notice.dto.NoticeListItemResponse;
import com.gongbang.workspace.notice.dto.NoticeUnreadCountResponse;
import com.gongbang.workspace.notice.repository.NoticeReadRepository;
import com.gongbang.workspace.notice.repository.NoticeRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 팀 공지 조회 (NTC-01 목록, NTC-02 상세·읽음, NTC-05 배너, 탭 배지). 모두 현재 팀원만 본다 (403 NOT_TEAM_MEMBER).
 *
 * <p>안 읽음 = 내가 쓴 공지가 아니고 notice_read에 내 기록이 없는 공지.
 */
@Service
@RequiredArgsConstructor
public class NoticeQueryService {

    static final int MAX_PAGE_SIZE = 50;

    /** 고정 공지 맨 위 → 최신순. 같은 초에 쓴 공지는 나중 ID가 위. */
    static final Sort PINNED_FIRST_LATEST =
            Sort.by(Sort.Order.desc("pinned"), Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    /** 회원 행이 없는(작성자 NULL) 공지의 표시 이름. MemberService.displayName의 탈퇴 표시와 같다. */
    static final String WITHDRAWN_NAME = "탈퇴한 사용자";

    private final NoticeRepository noticeRepository;
    private final NoticeReadRepository noticeReadRepository;
    private final TeamMemberService teamMemberService;
    private final MemberService memberService;

    /** NTC-01 목록. */
    @Transactional(readOnly = true)
    public PageResponse<NoticeListItemResponse> getNotices(
            Long memberId, Long teamId, int page, int size) {
        teamMemberService.requireMember(teamId, memberId);
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        Page<Notice> notices =
                noticeRepository.findByTeamId(
                        teamId, PageRequest.of(page, size, PINNED_FIRST_LATEST));
        List<Notice> content = notices.getContent();
        Set<Long> readIds = readNoticeIds(memberId, content);
        Map<Long, NoticeAuthor> authors = authors(content);

        List<NoticeListItemResponse> items =
                content.stream()
                        .map(
                                notice ->
                                        NoticeListItemResponse.of(
                                                notice,
                                                authorOf(notice, authors),
                                                isUnread(notice, memberId, readIds)))
                        .toList();
        return PageResponse.of(items, page, size, notices.getTotalElements());
    }

    /**
     * NTC-02 상세. 읽음 기록을 남긴다 (이미 있으면 그대로, 작성자 본인도 남김). 쓰기가 있어 readOnly가 아니다.
     *
     * <p>공지를 먼저 찾고(없으면 404) 공지의 팀으로 팀원인지 본다.
     */
    @Transactional
    public NoticeDetailResponse getNotice(Long memberId, Long noticeId) {
        Notice notice =
                noticeRepository
                        .findById(noticeId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        teamMemberService.requireMember(notice.getTeamId(), memberId);
        noticeReadRepository.markRead(notice.getId(), memberId);
        return NoticeDetailResponse.of(notice, author(notice.getAuthorId()));
    }

    /** NTC-05 고정 배너. 고정 공지가 없으면 null (응답 data null). 읽음 기록은 남기지 않는다. */
    @Transactional(readOnly = true)
    public NoticeDetailResponse getPinned(Long memberId, Long teamId) {
        teamMemberService.requireMember(teamId, memberId);
        return noticeRepository
                .findByTeamIdAndPinnedTrue(teamId)
                .map(notice -> NoticeDetailResponse.of(notice, author(notice.getAuthorId())))
                .orElse(null);
    }

    /** 공지 탭 배지. */
    @Transactional(readOnly = true)
    public NoticeUnreadCountResponse getUnreadCount(Long memberId, Long teamId) {
        teamMemberService.requireMember(teamId, memberId);
        return new NoticeUnreadCountResponse(noticeRepository.countUnread(teamId, memberId));
    }

    // ---------- 내부 ----------

    private Set<Long> readNoticeIds(Long memberId, List<Notice> notices) {
        if (notices.isEmpty()) {
            return Set.of();
        }
        List<Long> ids = notices.stream().map(Notice::getId).toList();
        return new HashSet<>(noticeReadRepository.findReadNoticeIds(memberId, ids));
    }

    private static boolean isUnread(Notice notice, Long memberId, Set<Long> readIds) {
        boolean mine = Objects.equals(notice.getAuthorId(), memberId);
        return !mine && !readIds.contains(notice.getId());
    }

    /**
     * 같은 작성자는 한 번만 읽는다. TODO(확인 필요): 회원 여러 명을 한 번에 읽는 메서드(정진우 member)가 생기면 그걸로 바꾼다
     * (PostQueryService와 같은 사정).
     */
    private Map<Long, NoticeAuthor> authors(List<Notice> notices) {
        Set<Long> authorIds =
                notices.stream()
                        .map(Notice::getAuthorId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
        Map<Long, NoticeAuthor> authors = new HashMap<>();
        for (Long authorId : authorIds) {
            authors.put(authorId, author(authorId));
        }
        return authors;
    }

    private static NoticeAuthor authorOf(Notice notice, Map<Long, NoticeAuthor> authors) {
        if (notice.getAuthorId() == null) {
            return new NoticeAuthor(null, WITHDRAWN_NAME);
        }
        return authors.get(notice.getAuthorId());
    }

    private NoticeAuthor author(Long authorId) {
        if (authorId == null) {
            return new NoticeAuthor(null, WITHDRAWN_NAME);
        }
        return new NoticeAuthor(authorId, memberService.displayName(authorId));
    }
}
