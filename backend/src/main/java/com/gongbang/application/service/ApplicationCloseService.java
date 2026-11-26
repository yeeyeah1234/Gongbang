package com.gongbang.application.service;

import com.gongbang.application.domain.Application;
import com.gongbang.application.domain.ApplicationStatus;
import com.gongbang.application.domain.RejectType;
import com.gongbang.application.repository.ApplicationRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 모집글 도메인이 부르는 지원 정리. 다른 도메인 service를 부르지 않는다: ApplicationCommandService는 모집글 service를 부르므로, 모집글
 * 삭제가 그쪽을 부르면 순환이 생긴다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ApplicationCloseService {

    private final ApplicationRepository applicationRepository;

    @Transactional(readOnly = true)
    public List<Long> findAllApplicantIds(Long postId) {
        return applicationRepository.findApplicantIdsForAdmin(postId);
    }

    /**
     * PST-004 ④: 모집글 삭제로 대기 중인 지원을 모두 종료(CLOSED)한다. 부르는 쪽 트랜잭션 안에서 모집글 행을 잠근 뒤 부른다.
     *
     * @return 종료된 지원의 지원자 ID (삭제 알림용)
     */
    public List<Long> closePendingApplications(Long postId, LocalDateTime now) {
        List<Application> pending =
                applicationRepository.findByPostIdAndStatusForUpdate(
                        postId, ApplicationStatus.PENDING);
        pending.forEach(application -> application.close(now));
        return pending.stream().map(Application::getApplicantId).toList();
    }

    /**
     * INT-005 ③: 팀 초대를 수락한 회원이 이 글에 대기 중인 지원이 있으면 「수락」으로 바꾼다. 대기 지원이 없거나 이미 처리된 지원(거절 등)이면 그대로 둔다.
     * 부르는 쪽 트랜잭션 안에서 모집글 행을 잠근 뒤 부른다 (잠금 순서 모집글 → 지원).
     *
     * @return 대기 지원을 수락으로 바꿨으면 true
     */
    public boolean acceptPendingApplication(Long postId, Long applicantId, LocalDateTime now) {
        return applicationRepository
                .findByPostIdAndApplicantIdForUpdate(postId, applicantId)
                .filter(Application::isPending)
                .map(
                        application -> {
                            application.accept(now);
                            return true;
                        })
                .orElse(false);
    }

    /**
     * REC-006 ④: 모집완료(자동·수동)로 남은 대기 지원을 모두 자동 거절(AUTO)한다. 부르는 쪽 트랜잭션 안에서 모집글 행을 잠근 뒤 부른다.
     *
     * @return 거절된 지원의 지원자 ID (거절 알림용)
     */
    public List<Long> autoRejectPendingApplications(Long postId, LocalDateTime now) {
        List<Application> pending =
                applicationRepository.findByPostIdAndStatusForUpdate(
                        postId, ApplicationStatus.PENDING);
        pending.forEach(application -> application.reject(RejectType.AUTO, now));
        return pending.stream().map(Application::getApplicantId).toList();
    }
}
