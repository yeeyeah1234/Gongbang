package com.gongbang.post.service;

import com.gongbang.application.service.ApplicationCloseService;
import com.gongbang.contest.service.ContestDetailService;
import com.gongbang.contest.service.ContestQueryService;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.global.validation.UrlPolicy;
import com.gongbang.invitation.service.InvitationCancelService;
import com.gongbang.post.domain.ExternalContest;
import com.gongbang.post.domain.PostStatus;
import com.gongbang.post.domain.RecruitPost;
import com.gongbang.post.dto.MyPostDeleteResponse;
import com.gongbang.post.dto.PostCompleteResponse;
import com.gongbang.post.dto.PostCreateRequest;
import com.gongbang.post.dto.PostCreateResponse;
import com.gongbang.post.dto.PostDetailResponse;
import com.gongbang.post.dto.PostUpdateRequest;
import com.gongbang.post.repository.RecruitPostRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 모집글 작성·수정·삭제. */
@Service
@RequiredArgsConstructor
@Transactional
public class PostCommandService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    // 기능정의서 PST-002 ①②의 범위
    static final int TITLE_MAX_LENGTH = 50;
    static final int CONTENT_MAX_LENGTH = 2000;
    static final int EXTERNAL_TITLE_MAX_LENGTH = 50;
    static final int TARGET_COUNT_MIN = 2;
    static final int TARGET_COUNT_MAX = 8;
    // DB 명세서 related_url·ext_contest_url VARCHAR(500)
    static final int URL_MAX_LENGTH = 500;

    private final RecruitPostRepository recruitPostRepository;
    private final ContestDetailService contestDetailService;
    private final ContestQueryService contestQueryService;
    private final PostDetailService postDetailService;
    private final ApplicationCloseService applicationCloseService;
    private final InvitationCancelService invitationCancelService;
    private final RecruitNotifier recruitNotifier;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * PST-002 모집글 작성. 작성자가 확정 인원 1명인 「모집중」 글을 만든다. 팀은 첫 수락 때 만든다 (INT-006).
     *
     * @param memberId 로그인 회원 ID (인증 모듈에서 받는다)
     */
    public PostCreateResponse create(Long memberId, PostCreateRequest request) {
        return create(memberId, request, LocalDate.now(KST));
    }

    PostCreateResponse create(Long memberId, PostCreateRequest request, LocalDate today) {
        if (request.contestId() != null && request.external() != null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (request.contestId() == null && request.external() == null) {
            throw new BusinessException(ErrorCode.CONTEST_REQUIRED);
        }
        String title = requireText(request.title(), TITLE_MAX_LENGTH);
        String content = requireText(request.content(), CONTENT_MAX_LENGTH);
        int targetCount = requireTargetCount(request.targetCount());
        String relatedUrl = optionalUrl(request.relatedUrl());
        LocalDate startDate = require(request.recruitStartDate());
        LocalDate endDate = require(request.recruitEndDate());

        RecruitPost post;
        if (request.contestId() != null) {
            LocalDate deadline =
                    contestDetailService
                            .findEndDate(request.contestId())
                            .orElseThrow(() -> new BusinessException(ErrorCode.CONTEST_NOT_FOUND));
            validatePeriod(startDate, endDate, deadline, today);
            post =
                    RecruitPost.withContest(
                            memberId,
                            request.contestId(),
                            title,
                            content,
                            targetCount,
                            startDate,
                            endDate,
                            relatedUrl);
        } else {
            ExternalContest externalContest = toExternalContest(request.external());
            validatePeriod(startDate, endDate, externalContest.getDeadline(), today);
            post =
                    RecruitPost.withExternalContest(
                            memberId,
                            externalContest,
                            title,
                            content,
                            targetCount,
                            startDate,
                            endDate,
                            relatedUrl);
        }
        Long postId = recruitPostRepository.save(post).getId();
        // ⑤ 외부 공모전 링크의 대표 이미지는 등록이 커밋된 뒤 따로 불러온다 (ExternalContestImageListener)
        ExternalContest external = post.getExternalContest();
        if (external != null && external.getUrl() != null) {
            eventPublisher.publishEvent(new ExternalContestLinkedEvent(postId, external.getUrl()));
        }
        return new PostCreateResponse(postId);
    }

    /**
     * PST-002 ⑤: 불러온 대표 이미지를 저장한다. 그사이 글이 지워졌거나 이미 이미지가 있으면 두지 않는다.
     *
     * @param pageUrl 이미지를 불러온 공모전 링크. 글의 링크와 다르면 저장하지 않는다.
     */
    public void saveExternalImage(Long postId, String pageUrl, String imageUrl) {
        recruitPostRepository
                .findById(postId)
                .filter(post -> post.getDeletedAt() == null)
                .map(RecruitPost::getExternalContest)
                .filter(external -> pageUrl.equals(external.getUrl()))
                .filter(external -> external.getImageUrl() == null)
                .ifPresent(external -> external.changeImageUrl(imageUrl));
    }

    private ExternalContest toExternalContest(PostCreateRequest.External external) {
        String title = requireText(external.title(), EXTERNAL_TITLE_MAX_LENGTH);
        if (!StringUtils.hasText(external.categoryCode())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        Long categoryId =
                contestQueryService
                        .findCategoryId(external.categoryCode().trim())
                        .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT));
        LocalDate deadline = require(external.deadline());
        String url = optionalUrl(external.url());
        // ⑤ 대표 이미지는 등록 뒤 따로 채운다. 그 전이나 못 불러오면 null이고 화면은 기본 이미지를 쓴다.
        return ExternalContest.of(title, categoryId, deadline, url);
    }

    /** PST-002 ②③: 마감된 공모전은 막고, 시작일 ≤ 종료일 ≤ 공모전 마감일이어야 한다. */
    private void validatePeriod(
            LocalDate startDate, LocalDate endDate, LocalDate deadline, LocalDate today) {
        if (deadline.isBefore(today)) {
            throw new BusinessException(ErrorCode.CONTEST_ENDED);
        }
        validatePeriod(startDate, endDate, deadline);
    }

    /** PST-002 ③: 시작일 ≤ 종료일 ≤ 공모전 마감일. */
    private static void validatePeriod(LocalDate startDate, LocalDate endDate, LocalDate deadline) {
        if (endDate.isBefore(startDate) || endDate.isAfter(deadline)) {
            throw new BusinessException(ErrorCode.INVALID_PERIOD);
        }
    }

    /**
     * PST-003 모집글 수정. 작성자만 할 수 있고(403 NOT_POST_AUTHOR), 모집중·모집완료 모두 고칠 수 있다 (①). 목표 인원은 수락과 겹치지 않게
     * 모집글 행을 잠근 뒤 확정 인원과 비교한다 (④, AGENTS.md 7).
     *
     * @return 수정된 상세 (조회수는 올리지 않는다)
     */
    public PostDetailResponse update(Long memberId, Long postId, PostUpdateRequest request) {
        return update(memberId, postId, request, LocalDate.now(KST));
    }

    PostDetailResponse update(
            Long memberId, Long postId, PostUpdateRequest request, LocalDate today) {
        RecruitPost post = lockOwnPost(memberId, postId);

        String title =
                request.title() == null
                        ? post.getTitle()
                        : requireText(request.title(), TITLE_MAX_LENGTH);
        String content =
                request.content() == null
                        ? post.getContent()
                        : requireText(request.content(), CONTENT_MAX_LENGTH);
        int targetCount = post.getTargetCount();
        if (request.targetCount() != null) {
            targetCount = requireTargetCount(request.targetCount());
            validateTargetAgainstCurrent(post, targetCount);
        }
        LocalDate startDate =
                request.recruitStartDate() == null
                        ? post.getRecruitStartDate()
                        : request.recruitStartDate();
        LocalDate endDate =
                request.recruitEndDate() == null
                        ? post.getRecruitEndDate()
                        : request.recruitEndDate();
        if (request.recruitStartDate() != null || request.recruitEndDate() != null) {
            // 이미 마감된 공모전의 글도 제목 등은 고칠 수 있어야 해서, 수정 때는 CONTEST_ENDED로 막지 않는다
            validatePeriod(startDate, endDate, contestDeadline(post));
        }
        String relatedUrl =
                request.relatedUrl() == null
                        ? post.getRelatedUrl()
                        : optionalUrl(request.relatedUrl());

        post.update(title, content, targetCount, startDate, endDate, relatedUrl);
        return postDetailService.describe(post, memberId, today);
    }

    /**
     * PST-004 모집글 삭제. 작성자만 할 수 있고(403 NOT_POST_AUTHOR), 팀원이 있는 글도 소프트 삭제한다 (④). 수락과 겹치지 않게 모집글 행을
     * 잠근 뒤 처리한다.
     */
    public void delete(Long memberId, Long postId) {
        delete(memberId, postId, LocalDateTime.now(KST));
    }

    void delete(Long memberId, Long postId, LocalDateTime now) {
        RecruitPost post = lockOwnPost(memberId, postId);
        post.delete(now);
        List<Long> closedApplicantIds =
                applicationCloseService.closePendingApplications(postId, now);
        // ④, INT-003 ④: 대기 중이던 지원자에게 삭제 알림
        closedApplicantIds.forEach(
                applicantId -> recruitNotifier.postDeleted(post, applicantId, memberId));
        // ④ 대기 중인 팀 초대도 모두 취소한다 (team_invitation.status CANCELED, 김건우 InvitationCancelService)
        invitationCancelService.cancelPendingByPostId(postId, now);
    }

    /**
     * MY-02 ③⑥ 내 모집글 선택 삭제. 고른 글마다 PST-004와 같은 규칙으로 처리하고(소프트 삭제, 대기 지원 CLOSED·알림), 하나라도 내 글이 아니거나
     * 없으면 그 에러(403 NOT_POST_AUTHOR · 404 POST_NOT_FOUND)로 전체를 되돌린다. 같은 ID는 한 번만 지우고, 여러 행을 잠글 때 순서가
     * 엇갈리지 않게 ID 순으로 잠근다.
     */
    public MyPostDeleteResponse deleteMine(Long memberId, List<Long> postIds) {
        return deleteMine(memberId, postIds, LocalDateTime.now(KST));
    }

    MyPostDeleteResponse deleteMine(Long memberId, List<Long> postIds, LocalDateTime now) {
        // List.of()로 만든 목록은 contains(null)이 예외를 던지므로 하나씩 본다
        if (postIds == null || postIds.isEmpty() || postIds.stream().anyMatch(Objects::isNull)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        List<Long> ids = postIds.stream().distinct().sorted().toList();
        ids.forEach(postId -> delete(memberId, postId, now));
        return new MyPostDeleteResponse(ids.size());
    }

    /**
     * REC-006 ② 수동 모집 완료. 작성자만 할 수 있고(403 NOT_POST_AUTHOR), 이미 모집완료면 409 POST_COMPLETED. 정원이 안 찼는지
     * 묻는 확인 모달은 화면에서 띄운다. 수락과 겹치지 않게 모집글 행을 잠근 뒤 처리한다.
     *
     * <p>③ 완료 뒤 새 지원·수락은 지원·수락 쪽에서 모집중이 아니면 막는다. ④ 남은 대기 지원은 자동 거절하고 거절 알림을 보낸다(보낸 사람 없음, 자동 완료와
     * 같음). 대기 중인 팀 초대도 취소한다(INT-005 ④). ⑤ 팀·채팅방은 손대지 않는다.
     */
    public PostCompleteResponse complete(Long memberId, Long postId) {
        return complete(memberId, postId, LocalDateTime.now(KST));
    }

    PostCompleteResponse complete(Long memberId, Long postId, LocalDateTime now) {
        RecruitPost post = lockOwnPost(memberId, postId);
        if (!post.isRecruiting()) {
            throw new BusinessException(ErrorCode.POST_COMPLETED);
        }
        post.complete(now);
        List<Long> rejectedApplicantIds =
                applicationCloseService.autoRejectPendingApplications(postId, now);
        rejectedApplicantIds.forEach(
                applicantId -> recruitNotifier.rejected(post, applicantId, null));
        // 모집완료 글로는 초대할 수 없으므로(INT-005 ④) 대기 중인 팀 초대도 취소한다 (team_invitation.status CANCELED)
        invitationCancelService.cancelPendingByPostId(postId, now);
        return new PostCompleteResponse(rejectedApplicantIds.size());
    }

    /**
     * 팀 도메인용 (WS-09 팀 나가기·NET-06 강제 퇴장·MY-11 탈퇴): 확정 인원을 1 줄이고 줄어든 인원을 돌려준다 (INT-006 ④, NET-06 응답
     * currentCount). 모집글 행을 잠근 뒤 줄여서 수락과 겹치지 않는다 (AGENTS.md 7). 모집완료 글은 모집중으로 되돌리지 않는다 (⑤,
     * REC-007). 모집중인 글은 빈 자리만큼 다시 수락할 수 있다.
     *
     * <p>부르는 쪽 트랜잭션 안에서 team_member의 left_at을 기록하는 것과 같이 부른다. 잠금 순서는 recruit_post → team이므로 팀 행을
     * 잠그기 전에 부른다. 삭제된 글의 팀도 남아 있으므로 삭제된 글이어도 줄인다 (PST-004 ④).
     */
    public int decreaseCurrentCount(Long postId) {
        RecruitPost post =
                recruitPostRepository
                        .findByIdForUpdate(postId)
                        .orElseThrow(
                                () -> new IllegalStateException("팀의 모집글이 없습니다: postId=" + postId));
        post.removeConfirmedMember();
        return post.getCurrentCount();
    }

    /**
     * 다른 도메인(지원 REC-001 등)용: 부르는 쪽 트랜잭션 안에서 모집글 행을 잠근다 (SELECT … FOR UPDATE). 없거나 삭제된 글이면 404
     * POST_NOT_FOUND. 잠금은 그 트랜잭션이 끝날 때 풀린다.
     */
    public RecruitPost lockPost(Long postId) {
        return recruitPostRepository
                .findByIdForUpdate(postId)
                .filter(found -> found.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
    }

    /** 모집글 행을 잠그고, 없거나 삭제된 글이면 404, 작성자가 아니면 403. */
    private RecruitPost lockOwnPost(Long memberId, Long postId) {
        RecruitPost post = lockPost(postId);
        if (!post.getAuthorId().equals(memberId)) {
            throw new BusinessException(ErrorCode.NOT_POST_AUTHOR);
        }
        return post;
    }

    /** PST-003 ④: 확정 인원보다 적게는 안 되고, 모집중인 글은 같게도 안 된다 (11/13 결정). */
    private static void validateTargetAgainstCurrent(RecruitPost post, int targetCount) {
        if (targetCount < post.getCurrentCount()) {
            throw new BusinessException(ErrorCode.TARGET_BELOW_CURRENT);
        }
        if (post.getStatus() == PostStatus.RECRUITING && targetCount == post.getCurrentCount()) {
            throw new BusinessException(ErrorCode.TARGET_EQUALS_CURRENT);
        }
    }

    private LocalDate contestDeadline(RecruitPost post) {
        if (post.getExternalContest() != null) {
            return post.getExternalContest().getDeadline();
        }
        return contestDetailService
                .findEndDate(post.getContestId())
                .orElseThrow(() -> new IllegalStateException("모집글의 공모전이 없습니다"));
    }

    /** 앞뒤 공백을 뺀 값이 1자 이상, max자 이하인지 본다. 글자 수는 DB(utf8mb4)처럼 문자 단위로 센다. */
    private static String requireText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        String stripped = value.strip();
        if (stripped.codePointCount(0, stripped.length()) > maxLength) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return stripped;
    }

    private static int requireTargetCount(Integer targetCount) {
        if (targetCount == null
                || targetCount < TARGET_COUNT_MIN
                || targetCount > TARGET_COUNT_MAX) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return targetCount;
    }

    private static <T> T require(T value) {
        if (value == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return value;
    }

    /**
     * PST-002 ④: 빈 값이면 null, 있으면 공통 URL 정책(#120, 대소문자 무관 http·https, 호스트 필수)에 맞는 주소만 받는다
     * (javascript: 등 차단). 기능정의서에 서버 문구가 없어 INVALID_INPUT 기본 문구를 쓴다.
     */
    private static String optionalUrl(String value) {
        String url;
        try {
            url = UrlPolicy.validateOrNull(value);
        } catch (BusinessException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (url != null && url.length() > URL_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return url;
    }
}
