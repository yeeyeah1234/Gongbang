package com.gongbang.application.service;

import com.gongbang.application.domain.Application;
import com.gongbang.application.domain.ApplicationStatus;
import com.gongbang.application.domain.RejectType;
import com.gongbang.application.dto.ApplicationAcceptResponse;
import com.gongbang.application.dto.ApplicationCreateResponse;
import com.gongbang.application.repository.ApplicationRepository;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.post.domain.PostStatus;
import com.gongbang.post.domain.RecruitPost;
import com.gongbang.post.dto.TeamJoinResult;
import com.gongbang.post.service.PostCommandService;
import com.gongbang.post.service.PostMembershipService;
import com.gongbang.post.service.PostQueryService;
import com.gongbang.post.service.RecruitNotifier;
import com.gongbang.post.service.TeamJoinService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 지원하기·취소·수락·거절. */
@Service
@RequiredArgsConstructor
@Transactional
public class ApplicationCommandService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ApplicationRepository applicationRepository;
    private final PostCommandService postCommandService;
    private final PostQueryService postQueryService;
    private final TeamJoinService teamJoinService;
    private final RecruitNotifier recruitNotifier;
    private final PostMembershipService postMembershipService;

    /**
     * REC-001 지원하기. 「대기」 지원을 만든다 (②). 모집 시작일 전에도 지원할 수 있고 종료일 당일까지 받는다 (11/10·11/13 결정). 모집완료
     * 처리(REC-006)와 겹치지 않게 모집글 행을 잠근 뒤 확인한다.
     *
     * @param memberId 로그인 회원 ID (인증 모듈에서 받는다)
     */
    public ApplicationCreateResponse apply(Long memberId, Long postId) {
        return apply(memberId, postId, LocalDate.now(KST));
    }

    ApplicationCreateResponse apply(Long memberId, Long postId, LocalDate today) {
        RecruitPost post = postCommandService.lockPost(postId);
        if (post.getAuthorId().equals(memberId)) {
            throw new BusinessException(ErrorCode.CANNOT_APPLY_OWN_POST);
        }
        if (post.getStatus() == PostStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.POST_COMPLETED);
        }
        if (today.isAfter(post.getRecruitEndDate())) {
            throw new BusinessException(ErrorCode.RECRUIT_PERIOD_ENDED);
        }
        if (applicationRepository.existsByPostIdAndApplicantId(postId, memberId)) {
            throw new BusinessException(ErrorCode.ALREADY_APPLIED);
        }
        // ③: 지원 기록 없이 초대로 들어온 현재 팀원도 막는다. 나간 팀원은 지원 기록이 없으면 다시 지원할 수 있다.
        if (postMembershipService.isActiveMember(postId, memberId)) {
            throw new BusinessException(ErrorCode.ALREADY_TEAM_MEMBER);
        }

        Application saved;
        try {
            saved = applicationRepository.saveAndFlush(Application.pending(postId, memberId));
        } catch (DataIntegrityViolationException e) {
            // 위 확인과 저장 사이에 같은 지원이 먼저 들어온 경우 (UQ post_id, applicant_id)
            throw new BusinessException(ErrorCode.ALREADY_APPLIED);
        }
        recruitNotifier.newApplication(post, memberId); // ⑤, INT-003 ①
        return new ApplicationCreateResponse(saved.getId());
    }

    /**
     * REC-002 지원 취소. 본인의 대기 지원만 지울 수 있다 (①⑤). 행을 지우므로 모집중이면 다시 지원할 수 있다 (③). 작성자의 수락·거절과 겹치지 않게 지원
     * 행을 잠근 뒤 상태를 확인한다 (④).
     *
     * @param memberId 로그인 회원 ID (인증 모듈에서 받는다)
     */
    public void cancel(Long memberId, Long applicationId) {
        Application application =
                applicationRepository
                        .findByIdForUpdate(applicationId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.APPLICATION_NOT_FOUND));
        if (!application.getApplicantId().equals(memberId)) {
            throw new BusinessException(ErrorCode.NOT_APPLICANT);
        }
        if (application.getStatus() != ApplicationStatus.PENDING) {
            throw new BusinessException(ErrorCode.ALREADY_PROCESSED);
        }
        applicationRepository.delete(application);
    }

    /**
     * REC-004 지원자 수락. 작성자만, 대기 지원만 수락한다 (①). 지원을 수락으로 바꾸고 확정 팀원으로 등록하며 모집 인원을 1 올린다 (②, INT-005 공통
     * 처리). 정원이 찼으면 409 POST_FULL (③). 정원에 닿으면 자동 모집완료 (⑤). 지원자에게 수락 알림 (⑥).
     *
     * <p>잠금 순서는 모집글 → 지원 (수정·삭제·REC-001과 같음). 한 자리 남은 상태에서 수락이 동시에 들어와도 모집글 행 잠금 때문에 하나씩 처리되어 하나만
     * 성공한다 (④).
     *
     * <p>READ COMMITTED로 실행한다. MySQL 기본(REPEATABLE READ)에서는 잠금 전에 읽은 순간(모집글 ID 조회)으로 읽기 시점이 고정되어,
     * 잠금을 기다리는 사이 앞선 수락이 만든 팀·팀원을 보지 못하고 팀을 또 만들거나 같은 색 번호를 고른다 (MySQL 동시 수락 테스트에서 확인).
     *
     * @param memberId 로그인 회원 ID (인증 모듈에서 받는다)
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ApplicationAcceptResponse accept(Long memberId, Long applicationId) {
        return accept(memberId, applicationId, LocalDateTime.now(KST));
    }

    ApplicationAcceptResponse accept(Long memberId, Long applicationId, LocalDateTime now) {
        // 지원 엔티티를 먼저 읽어 두면 뒤의 잠금 조회가 그 옛 값을 돌려주므로, 잠그기 전에는 모집글 ID만 값으로 읽는다
        Long postId =
                applicationRepository
                        .findPostIdById(applicationId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.APPLICATION_NOT_FOUND));
        RecruitPost post = postCommandService.lockPost(postId);
        if (!post.getAuthorId().equals(memberId)) {
            throw new BusinessException(ErrorCode.NOT_POST_AUTHOR);
        }
        Application application =
                applicationRepository
                        .findByIdForUpdate(applicationId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.APPLICATION_NOT_FOUND));
        if (!application.isPending()) {
            throw new BusinessException(ErrorCode.ALREADY_PROCESSED);
        }

        // 자동 모집완료 때 남은 대기 지원을 거절하는데, 이 지원은 그 대상에서 빠지도록 먼저 수락으로 바꾼다
        application.accept(now);
        TeamJoinResult result = teamJoinService.join(postId, application.getApplicantId(), now);
        recruitNotifier.accepted(post, application.getApplicantId(), memberId);
        return ApplicationAcceptResponse.from(result);
    }

    /**
     * REC-005 지원자 거절. 작성자만, 대기 지원만 거절한다 (①). 모집 인원은 그대로이고(②) 행이 남아 같은 글에 다시 지원할 수 없다 (③). 지원 행만
     * 잠근다: 수락(REC-004)은 모집글 → 지원 순서로 잠그므로, 여기서 모집글까지 잠그면 순서가 엇갈려 교착이 날 수 있다. 모집글은 작성자 확인용으로 읽기만 한다.
     *
     * @param memberId 로그인 회원 ID (인증 모듈에서 받는다)
     */
    public void reject(Long memberId, Long applicationId) {
        reject(memberId, applicationId, LocalDateTime.now(KST));
    }

    void reject(Long memberId, Long applicationId, LocalDateTime now) {
        Application application =
                applicationRepository
                        .findByIdForUpdate(applicationId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.APPLICATION_NOT_FOUND));
        RecruitPost post = postQueryService.getActivePost(application.getPostId());
        if (!post.getAuthorId().equals(memberId)) {
            throw new BusinessException(ErrorCode.NOT_POST_AUTHOR);
        }
        if (!application.isPending()) {
            throw new BusinessException(ErrorCode.ALREADY_PROCESSED);
        }
        application.reject(RejectType.MANUAL, now);
        recruitNotifier.rejected(post, application.getApplicantId(), memberId); // ④, INT-003 ③
    }
}
