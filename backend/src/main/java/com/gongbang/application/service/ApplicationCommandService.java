package com.gongbang.application.service;

import com.gongbang.application.domain.Application;
import com.gongbang.application.domain.ApplicationStatus;
import com.gongbang.application.domain.RejectType;
import com.gongbang.application.dto.ApplicationCreateResponse;
import com.gongbang.application.repository.ApplicationRepository;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.post.domain.PostStatus;
import com.gongbang.post.domain.RecruitPost;
import com.gongbang.post.service.PostCommandService;
import com.gongbang.post.service.PostQueryService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
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

    /**
     * REC-001 지원하기. 「대기」 지원을 만든다 (②). 모집 시작일 전에도 지원할 수 있고 종료일 당일까지 받는다 (11/10·11/13
     * 결정). 모집완료 처리(REC-006)와 겹치지 않게 모집글 행을 잠근 뒤 확인한다.
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
        // TODO(확인 필요): 팀원(team_member, 공통)이 생기면 이 글 팀의 현재 팀원(초대로 들어온 회원 포함)은
        // 409 ALREADY_TEAM_MEMBER로 막는다 (③).

        Application saved;
        try {
            saved = applicationRepository.saveAndFlush(Application.pending(postId, memberId));
        } catch (DataIntegrityViolationException e) {
            // 위 확인과 저장 사이에 같은 지원이 먼저 들어온 경우 (UQ post_id, applicant_id)
            throw new BusinessException(ErrorCode.ALREADY_APPLIED);
        }
        // TODO(확인 필요): NotificationService(정진우)가 생기면 작성자에게 APPLY_NEW 알림을 보낸다 (⑤, INT-003 ①).
        // 문구 「‘닉네임’님이 ‘제목’에 지원했어요」, 누르면 지원자 관리 모달.
        return new ApplicationCreateResponse(saved.getId());
    }

    /**
     * REC-002 지원 취소. 본인의 대기 지원만 지울 수 있다 (①⑤). 행을 지우므로 모집중이면 다시 지원할 수 있다 (③). 작성자의
     * 수락·거절과 겹치지 않게 지원 행을 잠근 뒤 상태를 확인한다 (④).
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
     * REC-005 지원자 거절. 작성자만, 대기 지원만 거절한다 (①). 모집 인원은 그대로이고(②) 행이 남아 같은 글에 다시 지원할 수 없다
     * (③). 지원 행만 잠근다: 수락(REC-004)은 모집글 → 지원 순서로 잠그므로, 여기서 모집글까지 잠그면 순서가 엇갈려 교착이 날
     * 수 있다. 모집글은 작성자 확인용으로 읽기만 한다.
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
        // TODO(확인 필요): NotificationService(정진우)가 생기면 지원자에게 APPLY_REJECTED 알림을 보낸다 (④, INT-003 ③).
        // 문구 「‘제목’ 지원이 거절되었어요」, 누르면 모집글 상세.
    }
}
