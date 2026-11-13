package com.gongbang.post.service;

import com.gongbang.contest.dto.ContestSummary;
import com.gongbang.contest.service.ContestLookupService;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.post.domain.MyStatus;
import com.gongbang.post.domain.RecruitPost;
import com.gongbang.post.dto.PostDetailResponse;
import com.gongbang.post.dto.PostDetailResponse.AuthorInfo;
import com.gongbang.post.repository.RecruitPostRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** PST-005 모집글 상세 (조회수 집계 포함). */
@Service
@RequiredArgsConstructor
public class PostDetailService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    // PST-005 ⑥: 같은 회원은 24시간에 한 번만 센다 (24시간은 제안값)
    static final long VIEW_INTERVAL_HOURS = 24;

    private final RecruitPostRepository recruitPostRepository;
    private final ContestLookupService contestLookupService;

    /**
     * PST-005 모집글 상세. 없거나 삭제된 글이면 404 POST_NOT_FOUND (PST-004 ⑤).
     *
     * @param viewerId 로그인 회원 ID, 비회원이면 null. 비회원은 조회수를 올리지 않는다 (⑥).
     */
    @Transactional
    public PostDetailResponse getPost(Long postId, Long viewerId) {
        return getPost(postId, viewerId, LocalDateTime.now(KST));
    }

    PostDetailResponse getPost(Long postId, Long viewerId, LocalDateTime now) {
        RecruitPost post =
                recruitPostRepository
                        .findById(postId)
                        .filter(found -> found.getDeletedAt() == null)
                        .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        int viewCount = post.getViewCount();
        if (viewerId != null && countView(postId, viewerId, now)) {
            recruitPostRepository.increaseViewCount(postId);
            viewCount++;
        }

        MyStatus myStatus = myStatus(post, viewerId);
        // TODO(확인 필요): 회원 도메인(정진우)이 생기면 닉네임·프로필 사진을 채운다 (INT-002 ①).
        AuthorInfo author = new AuthorInfo(post.getAuthorId(), null, null);

        if (post.getExternalContest() != null) {
            String categoryName =
                    contestLookupService
                            .findCategoryNames(Set.of(post.getExternalContest().getCategoryId()))
                            .get(post.getExternalContest().getCategoryId());
            return PostDetailResponse.of(
                    post, categoryName, author, viewCount, myStatus, null, now.toLocalDate());
        }
        ContestSummary contest =
                contestLookupService
                        .findSummary(post.getContestId())
                        .orElseThrow(() -> new IllegalStateException("모집글의 공모전이 없습니다"));
        return PostDetailResponse.of(
                post, contest, author, viewCount, myStatus, null, now.toLocalDate());
    }

    /** 24시간이 지난 기록을 갱신했거나 처음 기록했으면 true. */
    private boolean countView(Long postId, Long viewerId, LocalDateTime now) {
        LocalDateTime threshold = now.minusHours(VIEW_INTERVAL_HOURS);
        if (recruitPostRepository.touchStaleView(postId, viewerId, now, threshold) == 1) {
            return true;
        }
        return recruitPostRepository.insertViewIfAbsent(postId, viewerId, now) == 1;
    }

    private static MyStatus myStatus(RecruitPost post, Long viewerId) {
        if (viewerId == null) {
            return MyStatus.NONE;
        }
        if (Objects.equals(post.getAuthorId(), viewerId)) {
            return MyStatus.AUTHOR;
        }
        // TODO(확인 필요): 지원(application, REC-001)과 팀원(team_member) 테이블이 생기면
        // PENDING / REJECTED / ACCEPTED / LEFT와 myApplicationId를 채운다. 지금은 모두 NONE이다.
        return MyStatus.NONE;
    }
}
