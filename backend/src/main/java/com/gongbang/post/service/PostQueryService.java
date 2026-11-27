package com.gongbang.post.service;

import com.gongbang.comment.service.CommentCountService;
import com.gongbang.contest.dto.ContestSummary;
import com.gongbang.contest.service.ContestLookupService;
import com.gongbang.global.common.PageResponse;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.dto.MemberProfileSummary;
import com.gongbang.member.service.MemberService;
import com.gongbang.post.domain.PostSearchSort;
import com.gongbang.post.domain.PostStatus;
import com.gongbang.post.domain.RecruitPost;
import com.gongbang.post.dto.MyPostItemResponse;
import com.gongbang.post.dto.PostListItemResponse;
import com.gongbang.post.dto.PostListItemResponse.AuthorInfo;
import com.gongbang.post.dto.PostListItemResponse.ContestInfo;
import com.gongbang.post.repository.RecruitPostRepository;
import com.gongbang.post.repository.RecruitPostSearchRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 모집글 조회. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostQueryService {

    // 한 번에 받을 수 있는 최대 개수. 공모전 목록(MAIN-01)과 같은 값을 쓴다.
    static final int MAX_PAGE_SIZE = 50;

    // PST-001 ①: 최신 등록순. 같은 시각이면 나중에 들어온 글이 먼저.
    static final Sort LATEST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final RecruitPostRepository recruitPostRepository;
    private final ContestLookupService contestLookupService;
    private final MemberService memberService;
    private final CommentCountService commentCountService;
    private final RecruitPostSearchRepository recruitPostSearchRepository;

    /** 다른 도메인(지원자 목록 REC-003 등)용: 삭제되지 않은 모집글. 없거나 삭제됐으면 404 POST_NOT_FOUND. 잠그지 않는다. */
    public RecruitPost getActivePost(Long postId) {
        return recruitPostRepository
                .findById(postId)
                .filter(post -> post.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
    }

    /**
     * NET-02 팀 초대 모달(김건우 GET /api/members/me/recruiting-posts)용: 내가 쓴 모집중인 글, 최신순. 삭제된 글과 모집완료 글은
     * 뺀다 (INT-005 ④). 모집 종료일이 지난 「모집마감」 글은 저장된 상태가 모집중이라 넣는다 (초대 수락은 종료일 뒤에도 가능, REC-001).
     */
    public List<RecruitPost> getMyRecruitingPosts(Long authorId) {
        return recruitPostRepository
                .findByAuthorIdAndStatusAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                        authorId, PostStatus.RECRUITING);
    }

    /** PST-001 모집글 목록. 비회원도 본다 (⑤). 삭제된 글은 빠진다. */
    public PageResponse<PostListItemResponse> getPosts(int page, int size) {
        return getPosts(null, false, page, size);
    }

    /**
     * PST-001 모집글 목록과 ⑩ 거르기. contestId가 있으면 그 서비스 공모전의 글만(외부 공모전 글은 빠짐), recruiting이 true면 지금 지원할 수
     * 있는 글(모집중이고 모집 종료일이 오늘 이후, ④ 「모집마감」 제외)만 준다. 공모전 상세의 「팀원 모집 중」 칸(MAIN-02 ⑨)은
     * contestId·recruiting=true·size=3으로 부르고 전체 개수는 totalElements를 쓴다. 정렬은 모두 최신 등록순.
     */
    public PageResponse<PostListItemResponse> getPosts(
            Long contestId, boolean recruiting, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        PageRequest pageable = PageRequest.of(page, size, LATEST);
        LocalDate today = LocalDate.now(KST);
        Page<RecruitPost> posts;
        if (contestId != null && recruiting) {
            posts =
                    recruitPostRepository
                            .findByContestIdAndStatusAndRecruitEndDateGreaterThanEqualAndDeletedAtIsNull(
                                    contestId, PostStatus.RECRUITING, today, pageable);
        } else if (contestId != null) {
            posts = recruitPostRepository.findByContestIdAndDeletedAtIsNull(contestId, pageable);
        } else if (recruiting) {
            posts =
                    recruitPostRepository
                            .findByStatusAndRecruitEndDateGreaterThanEqualAndDeletedAtIsNull(
                                    PostStatus.RECRUITING, today, pageable);
        } else {
            posts = recruitPostRepository.findByDeletedAtIsNull(pageable);
        }
        return PageResponse.of(toItems(posts.getContent()), page, size, posts.getTotalElements());
    }

    /**
     * 통합 검색(SRCH-05 ①, SRCH-02 ②)의 모집글 부분 (최규선 SearchService가 부른다). 제목·본문에 검색어가 있거나 작성자가 authorIds에
     * 있는 글을 찾고, 삭제된 글(관리자 숨김 포함)은 뺀다. 모집완료·모집마감 글도 PST-001 목록처럼 넣는다. 항목은 PST-001 목록 항목과 같다.
     *
     * <p>sort: accuracy(기본, 제목 같음 > 제목 시작 > 제목 포함 > 본문·작성자만 일치, 같으면 최신순) | latest | deadline(지원할 수
     * 있는 글을 모집 종료일 빠른 순으로 앞에, 끝난 글은 뒤에 최신순). 검색어 길이(2자 이상) 검사는 부르는 쪽이 한다.
     */
    public PageResponse<PostListItemResponse> searchPosts(
            String keyword, Collection<Long> authorIds, String sort, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        String trimmed = keyword == null ? "" : keyword.strip();
        if (trimmed.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        PostSearchSort searchSort = PostSearchSort.from(sort);
        List<RecruitPost> posts =
                recruitPostSearchRepository.search(
                        trimmed,
                        authorIds,
                        searchSort,
                        LocalDate.now(KST),
                        (long) page * size,
                        size);
        long total = recruitPostSearchRepository.count(trimmed, authorIds);
        return PageResponse.of(toItems(posts), page, size, total);
    }

    /** PST-001 목록 항목: 공모전 정보와 작성자 닉네임을 한 번에 읽어 붙인다. */
    private List<PostListItemResponse> toItems(List<RecruitPost> posts) {
        Map<Long, ContestSummary> contests =
                contestLookupService.findSummaries(collect(posts, RecruitPost::getContestId));
        Map<Long, String> categoryNames =
                contestLookupService.findCategoryNames(
                        collect(
                                posts,
                                post ->
                                        post.getExternalContest() == null
                                                ? null
                                                : post.getExternalContest().getCategoryId()));
        Map<Long, String> nicknames = nicknames(posts);
        return posts.stream()
                .map(
                        post ->
                                PostListItemResponse.of(
                                        post,
                                        contestInfo(post, contests, categoryNames),
                                        new AuthorInfo(
                                                post.getAuthorId(),
                                                nicknames.get(post.getAuthorId()))))
                .toList();
    }

    /**
     * MY-02 · INT-002 ③ 마이페이지 「내 글 관리」 모집글 탭. 내가 쓴 글(삭제된 글 제외)을 최신순으로 준다 (④). 댓글 수는 삭제·숨김되지 않은
     * 댓글·답글·비공개 댓글을 센다 (NET-01).
     */
    public PageResponse<MyPostItemResponse> getMyPosts(Long memberId, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        Page<RecruitPost> posts =
                recruitPostRepository.findByAuthorIdAndDeletedAtIsNull(
                        memberId, PageRequest.of(page, size, LATEST));
        Map<Long, Long> commentCounts =
                commentCountService.countByPostIds(
                        posts.getContent().stream().map(RecruitPost::getId).toList());
        List<MyPostItemResponse> items =
                posts.getContent().stream()
                        .map(
                                post ->
                                        MyPostItemResponse.of(
                                                post, commentCounts.getOrDefault(post.getId(), 0L)))
                        .toList();
        return PageResponse.of(items, page, size, posts.getTotalElements());
    }

    private static ContestInfo contestInfo(
            RecruitPost post, Map<Long, ContestSummary> contests, Map<Long, String> categoryNames) {
        if (post.getExternalContest() != null) {
            return ContestInfo.of(
                    post.getExternalContest(),
                    categoryNames.get(post.getExternalContest().getCategoryId()));
        }
        ContestSummary contest = contests.get(post.getContestId());
        return contest == null ? null : ContestInfo.of(contest);
    }

    /**
     * PST-001 ② 작성자 닉네임 (INT-002 ①). 한 페이지의 작성자를 한 번에 읽는다. 탈퇴·삭제 회원은 MemberService가 「탈퇴한 사용자」로 준다
     * (MY-11).
     */
    private Map<Long, String> nicknames(List<RecruitPost> posts) {
        Set<Long> authorIds = collect(posts, RecruitPost::getAuthorId);
        Map<Long, MemberProfileSummary> summaries = memberService.getProfileSummaries(authorIds);
        return authorIds.stream()
                .collect(Collectors.toMap(Function.identity(), id -> summaries.get(id).nickname()));
    }

    private static Set<Long> collect(List<RecruitPost> posts, Function<RecruitPost, Long> idOf) {
        return posts.stream().map(idOf).filter(Objects::nonNull).collect(Collectors.toSet());
    }
}
