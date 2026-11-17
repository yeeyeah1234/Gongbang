package com.gongbang.member.service;

import com.gongbang.contest.dto.ContestCardResponse;
import com.gongbang.contest.service.ContestCardService;
import com.gongbang.global.common.PageResponse;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.domain.Bookmark;
import com.gongbang.member.repository.BookmarkRepository;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** MY-03 북마크 (저장은 최규선 메인 카드 버튼이 부른다) */
@Service
@RequiredArgsConstructor
public class BookmarkService {

    private static final Set<String> SORTS = Set.of("deadline", "bookmarked", "latest");
    private static final int MAX_SIZE = 50;

    private final BookmarkRepository bookmarkRepository;
    private final ContestCardService contestCardService;

    /** PUT·DELETE: 두 번 눌러도 같은 결과(멱등) */
    @Transactional
    public boolean setBookmark(Long memberId, Long contestId, boolean bookmarked) {
        if (bookmarkRepository.countContest(contestId) == 0) {
            throw new BusinessException(ErrorCode.CONTEST_NOT_FOUND);
        }
        Bookmark.Key key = new Bookmark.Key(memberId, contestId);
        if (bookmarked && !bookmarkRepository.existsById(key)) {
            bookmarkRepository.save(new Bookmark(memberId, contestId));
        } else if (!bookmarked) {
            bookmarkRepository.deleteById(key);
        }
        return bookmarked;
    }

    @Transactional(readOnly = true)
    public PageResponse<ContestCardResponse> getBookmarks(
            Long memberId, String sort, int page, int size) {
        if (!SORTS.contains(sort) || page < 0 || size < 1 || size > MAX_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_SORT);
        }
        List<Long> ids =
                bookmarkRepository.findContestIds(
                        memberId, LocalDate.now(), sort, size, page * size);
        // 공모전 카드 모양은 최규선 contest 도메인 service로 만든다 (AGENTS 3)
        List<ContestCardResponse> cards = contestCardService.getCardsByIds(ids, true);
        return PageResponse.of(cards, page, size, bookmarkRepository.countByMemberId(memberId));
    }

    /**
     * MAIN-06·MAIN-02: 공모전 목록·상세·검색 카드에 「내가 북마크했는지」를 표시할 때 최규선 contest 도메인이 부른다 (AGENTS 3).
     * 비로그인(memberId null)이거나 id 목록이 null·비어 있으면 빈 값. ContestCardService에서는 부르지 않는다
     * (BookmarkService가 ContestCardService를 써서 순환 의존이 됨).
     */
    @Transactional(readOnly = true)
    public Set<Long> findBookmarkedContestIds(Long memberId, Collection<Long> contestIds) {
        if (memberId == null || contestIds == null || contestIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(bookmarkRepository.findBookmarkedContestIds(memberId, contestIds));
    }
}
