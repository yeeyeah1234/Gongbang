package com.gongbang.board.service;

import com.gongbang.board.domain.BoardCategory;
import com.gongbang.board.domain.BoardPost;
import com.gongbang.board.dto.BoardPostDetailResponse;
import com.gongbang.board.dto.BoardPostListItemResponse;
import com.gongbang.board.dto.BoardPostListItemResponse.AuthorInfo;
import com.gongbang.board.dto.BoardPostListItemResponse.ContestInfo;
import com.gongbang.board.repository.BoardPostRepository;
import com.gongbang.contest.dto.ContestSummary;
import com.gongbang.contest.service.ContestLookupService;
import com.gongbang.global.common.PageResponse;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.dto.MemberProfileSummary;
import com.gongbang.member.service.MemberService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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

/** BRD-001 목록, BRD-004 상세(조회수), BRD-008 인기글. 비회원도 본다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoardPostQueryService {

    // 한 번에 받을 수 있는 최대 개수 (모집글 목록 PST-001과 같은 값)
    static final int MAX_PAGE_SIZE = 50;

    // BRD-004 ⑤: 같은 회원은 24시간에 한 번만 센다 (PST-005와 같은 제안값)
    static final long VIEW_INTERVAL_HOURS = 24;

    // BRD-008 ①②: 최근 7일, 3개 (제안값)
    static final int POPULAR_DAYS = 7;
    static final int POPULAR_SIZE = 3;

    // BRD-001 ⑨ 정렬. 같은 값이면 나중에 들어온 글이 먼저
    static final Sort LATEST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
    static final Sort POPULAR =
            Sort.by(
                    Sort.Order.desc("likeCount"),
                    Sort.Order.desc("viewCount"),
                    Sort.Order.desc("createdAt"),
                    Sort.Order.desc("id"));

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final BoardPostRepository boardPostRepository;
    private final ContestLookupService contestLookupService;
    private final MemberService memberService;
    private final BoardPostImageService boardPostImageService;

    /** 다른 도메인(댓글 BRD-005 등)용: 삭제되지 않은 커뮤니티 게시글. 없거나 삭제됐으면 404 BOARD_POST_NOT_FOUND. */
    public BoardPost getActivePost(Long boardPostId) {
        return boardPostRepository
                .findById(boardPostId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_POST_NOT_FOUND));
    }

    /** BRD-001 목록. sort는 latest(기본) 또는 popular, 그 밖의 값이면 400. */
    public PageResponse<BoardPostListItemResponse> getPosts(
            BoardCategory category, Long contestId, String sort, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        Page<BoardPost> posts =
                boardPostRepository.findActive(
                        category, contestId, PageRequest.of(page, size, toSort(sort)));
        return PageResponse.of(toItems(posts.getContent()), page, size, posts.getTotalElements());
    }

    /** BRD-008 인기글: 최근 7일 안에 쓴 글 중 좋아요 1개 이상, 최대 3개. 없으면 빈 목록. */
    public List<BoardPostListItemResponse> getPopular(BoardCategory category) {
        return getPopular(category, LocalDateTime.now(KST));
    }

    List<BoardPostListItemResponse> getPopular(BoardCategory category, LocalDateTime now) {
        List<BoardPost> posts =
                boardPostRepository.findPopular(
                        category, now.minusDays(POPULAR_DAYS), PageRequest.of(0, POPULAR_SIZE));
        return toItems(posts);
    }

    /**
     * BRD-004 상세. 없거나 삭제된 글이면 404. 로그인 회원은 24시간에 한 번 조회수 +1, 비회원은 올리지 않는다 (⑤).
     *
     * @param viewerId 로그인 회원 ID, 비회원이면 null
     */
    @Transactional
    public BoardPostDetailResponse getPost(Long boardPostId, Long viewerId) {
        return getPost(boardPostId, viewerId, LocalDateTime.now(KST));
    }

    BoardPostDetailResponse getPost(Long boardPostId, Long viewerId, LocalDateTime now) {
        BoardPost post =
                boardPostRepository
                        .findById(boardPostId)
                        .filter(found -> !found.isDeleted())
                        .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_POST_NOT_FOUND));
        int viewCount = post.getViewCount();
        if (viewerId != null && countView(boardPostId, viewerId, now)) {
            boardPostRepository.increaseViewCount(boardPostId);
            viewCount++;
        }
        return describe(post, viewerId, viewCount, now.toLocalDate());
    }

    /** 이미 읽은 글을 상세 응답으로 만든다. 조회수는 건드리지 않는다 (BRD-003 수정 응답). */
    BoardPostDetailResponse describe(BoardPost post, Long viewerId) {
        return describe(post, viewerId, post.getViewCount(), LocalDate.now(KST));
    }

    private BoardPostDetailResponse describe(
            BoardPost post, Long viewerId, int viewCount, LocalDate today) {
        BoardPostDetailResponse.ContestInfo contest =
                post.getContestId() == null
                        ? null
                        : contestLookupService
                                .findSummary(post.getContestId())
                                .map(
                                        summary ->
                                                BoardPostDetailResponse.ContestInfo.of(
                                                        summary, today))
                                .orElse(null);
        MemberProfileSummary author = memberService.getProfileSummary(post.getAuthorId());
        boolean isLiked =
                viewerId != null && boardPostRepository.countLike(post.getId(), viewerId) > 0;
        return BoardPostDetailResponse.of(
                post,
                contest,
                // BRD-006: 결과물 자랑만 사진이 있다
                post.getCategory() == BoardCategory.SHOWCASE
                        ? boardPostImageService.findImages(post.getId())
                        : List.of(),
                new BoardPostDetailResponse.AuthorInfo(
                        post.getAuthorId(), author.nickname(), author.profileImageUrl()),
                viewCount,
                isLiked,
                post.isWrittenBy(viewerId));
    }

    private static Sort toSort(String sort) {
        if (sort == null || sort.isBlank() || "latest".equals(sort)) {
            return LATEST;
        }
        if ("popular".equals(sort)) {
            return POPULAR;
        }
        throw new BusinessException(ErrorCode.INVALID_INPUT);
    }

    /** 공모전 이름과 작성자 닉네임을 한 번에 읽어 목록 항목으로 만든다. */
    private List<BoardPostListItemResponse> toItems(List<BoardPost> posts) {
        Set<Long> contestIds =
                posts.stream()
                        .map(BoardPost::getContestId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
        Map<Long, ContestSummary> contests = contestLookupService.findSummaries(contestIds);
        Map<Long, MemberProfileSummary> authors =
                memberService.getProfileSummaries(
                        posts.stream().map(BoardPost::getAuthorId).collect(Collectors.toSet()));
        return posts.stream()
                .map(
                        post -> {
                            ContestSummary contest =
                                    post.getContestId() == null
                                            ? null
                                            : contests.get(post.getContestId());
                            MemberProfileSummary author = authors.get(post.getAuthorId());
                            return BoardPostListItemResponse.of(
                                    post,
                                    contest == null
                                            ? null
                                            : new ContestInfo(contest.contestId(), contest.title()),
                                    new AuthorInfo(
                                            post.getAuthorId(),
                                            author == null ? "탈퇴한 사용자" : author.nickname()));
                        })
                .toList();
    }

    /** 24시간이 지난 기록을 갱신했거나 처음 기록했으면 true. */
    private boolean countView(Long boardPostId, Long viewerId, LocalDateTime now) {
        LocalDateTime threshold = now.minusHours(VIEW_INTERVAL_HOURS);
        if (boardPostRepository.touchStaleView(boardPostId, viewerId, now, threshold) == 1) {
            return true;
        }
        return boardPostRepository.insertViewIfAbsent(boardPostId, viewerId, now) == 1;
    }
}
