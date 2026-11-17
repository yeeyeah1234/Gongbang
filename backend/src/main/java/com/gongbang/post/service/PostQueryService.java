package com.gongbang.post.service;

import com.gongbang.contest.dto.ContestSummary;
import com.gongbang.contest.service.ContestLookupService;
import com.gongbang.global.common.PageResponse;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.post.domain.RecruitPost;
import com.gongbang.post.dto.PostListItemResponse;
import com.gongbang.post.dto.PostListItemResponse.AuthorInfo;
import com.gongbang.post.dto.PostListItemResponse.ContestInfo;
import com.gongbang.post.repository.RecruitPostRepository;
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

    private final RecruitPostRepository recruitPostRepository;
    private final ContestLookupService contestLookupService;

    /** 다른 도메인(지원자 목록 REC-003 등)용: 삭제되지 않은 모집글. 없거나 삭제됐으면 404 POST_NOT_FOUND. 잠그지 않는다. */
    public RecruitPost getActivePost(Long postId) {
        return recruitPostRepository
                .findById(postId)
                .filter(post -> post.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
    }

    /** PST-001 모집글 목록. 비회원도 본다 (⑤). 삭제된 글은 빠진다. */
    public PageResponse<PostListItemResponse> getPosts(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        Page<RecruitPost> posts =
                recruitPostRepository.findByDeletedAtIsNull(PageRequest.of(page, size, LATEST));

        Map<Long, ContestSummary> contests =
                contestLookupService.findSummaries(
                        collect(posts.getContent(), RecruitPost::getContestId));
        Map<Long, String> categoryNames =
                contestLookupService.findCategoryNames(
                        collect(
                                posts.getContent(),
                                post ->
                                        post.getExternalContest() == null
                                                ? null
                                                : post.getExternalContest().getCategoryId()));

        List<PostListItemResponse> items =
                posts.getContent().stream()
                        .map(
                                post ->
                                        PostListItemResponse.of(
                                                post,
                                                contestInfo(post, contests, categoryNames),
                                                authorInfo(post)))
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

    private static AuthorInfo authorInfo(RecruitPost post) {
        // TODO(확인 필요): 회원 도메인(정진우)이 생기면 닉네임을 한 번에 읽어 채운다 (INT-002 ①).
        // 탈퇴 회원은 nickname null → 화면 「탈퇴한 사용자」 (INT-002 ②). 지금은 모두 null이다.
        return new AuthorInfo(post.getAuthorId(), null);
    }

    private static Set<Long> collect(List<RecruitPost> posts, Function<RecruitPost, Long> idOf) {
        return posts.stream().map(idOf).filter(Objects::nonNull).collect(Collectors.toSet());
    }
}
