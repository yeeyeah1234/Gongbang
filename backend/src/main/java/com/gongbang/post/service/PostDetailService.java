package com.gongbang.post.service;

import com.gongbang.application.dto.MyApplication;
import com.gongbang.application.service.ApplicationQueryService;
import com.gongbang.contest.dto.ContestSummary;
import com.gongbang.contest.service.ContestLookupService;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.service.MemberService;
import com.gongbang.post.domain.MyStatus;
import com.gongbang.post.domain.RecruitPost;
import com.gongbang.post.dto.PostDetailResponse;
import com.gongbang.post.dto.PostDetailResponse.AuthorInfo;
import com.gongbang.post.repository.RecruitPostRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;
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
    private final ApplicationQueryService applicationQueryService;
    private final PostMembershipService postMembershipService;
    private final MemberService memberService;
    private final PostImageService postImageService;

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
        return describe(post, viewerId, viewCount, now.toLocalDate());
    }

    /** 이미 읽은 모집글을 상세 응답으로 만든다. 조회수는 건드리지 않는다 (PST-003 수정 응답 등). */
    PostDetailResponse describe(RecruitPost post, Long viewerId, LocalDate today) {
        return describe(post, viewerId, post.getViewCount(), today);
    }

    private PostDetailResponse describe(
            RecruitPost post, Long viewerId, int viewCount, LocalDate today) {
        Optional<MyApplication> myApplication = myApplication(post, viewerId);
        MyStatus myStatus = myStatus(post, viewerId, myApplication);
        Long myApplicationId = myApplication.map(MyApplication::applicationId).orElse(null);
        AuthorInfo author = author(post.getAuthorId());

        if (post.getExternalContest() != null) {
            String categoryName =
                    contestLookupService
                            .findCategoryNames(Set.of(post.getExternalContest().getCategoryId()))
                            .get(post.getExternalContest().getCategoryId());
            return PostDetailResponse.of(
                    post,
                    categoryName,
                    postImageService.findImages(post.getId()),
                    author,
                    viewCount,
                    myStatus,
                    myApplicationId,
                    today);
        }
        ContestSummary contest =
                contestLookupService
                        .findSummary(post.getContestId())
                        .orElseThrow(() -> new IllegalStateException("모집글의 공모전이 없습니다"));
        return PostDetailResponse.of(
                post, contest, author, viewCount, myStatus, myApplicationId, today);
    }

    /** 작성자 닉네임·프로필 사진 (INT-002 ①). 탈퇴한 회원은 목록과 같이 「탈퇴한 사용자」, 사진 없음 (MY-11). */
    private AuthorInfo author(Long authorId) {
        return memberService
                .findActiveMember(authorId)
                .map(m -> new AuthorInfo(authorId, m.getNickname(), m.getProfileImageUrl()))
                .orElseGet(
                        () -> new AuthorInfo(authorId, memberService.displayName(authorId), null));
    }

    /** 회원(작성자 제외)이 이 글에 낸 지원. */
    private Optional<MyApplication> myApplication(RecruitPost post, Long viewerId) {
        if (viewerId == null || Objects.equals(post.getAuthorId(), viewerId)) {
            return Optional.empty();
        }
        return applicationQueryService.findMyApplication(post.getId(), viewerId);
    }

    /** 24시간이 지난 기록을 갱신했거나 처음 기록했으면 true. */
    private boolean countView(Long postId, Long viewerId, LocalDateTime now) {
        LocalDateTime threshold = now.minusHours(VIEW_INTERVAL_HOURS);
        if (recruitPostRepository.touchStaleView(postId, viewerId, now, threshold) == 1) {
            return true;
        }
        return recruitPostRepository.insertViewIfAbsent(postId, viewerId, now) == 1;
    }

    /**
     * PST-006 버튼 분기용 상태. 팀원 여부가 지원 상태보다 앞선다: 현재 팀원이면 ACCEPTED(⑤, 초대로 들어온 회원과 재초대로 다시 들어온 회원 포함), 나간
     * 기록만 있으면 LEFT(⑨, 강제 퇴장 포함). 둘 다 아니면 지원 상태를 본다 (11/10·11/13 결정).
     */
    private MyStatus myStatus(
            RecruitPost post, Long viewerId, Optional<MyApplication> myApplication) {
        if (viewerId == null) {
            return MyStatus.NONE;
        }
        if (Objects.equals(post.getAuthorId(), viewerId)) {
            return MyStatus.AUTHOR;
        }
        switch (postMembershipService.membership(post.getId(), viewerId)) {
            case ACTIVE:
                return MyStatus.ACCEPTED;
            case LEFT:
                return MyStatus.LEFT;
            case NONE:
                break;
        }
        return myApplication
                .map(
                        application ->
                                switch (application.status()) {
                                    case PENDING -> MyStatus.PENDING;
                                    case REJECTED -> MyStatus.REJECTED;
                                        // 수락된 지원은 팀원 행이 함께 생기므로 위에서 ACCEPTED·LEFT로 갈린다. 팀원 행이 없으면
                                        // 팀에 없는 것으로 본다.
                                    case ACCEPTED -> MyStatus.NONE;
                                        // 삭제된 글의 지원이라 상세를 열 일이 없다
                                    case CLOSED -> MyStatus.NONE;
                                })
                .orElse(MyStatus.NONE);
    }
}
