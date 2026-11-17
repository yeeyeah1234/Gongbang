package com.gongbang.application.service;

import com.gongbang.application.domain.Application;
import com.gongbang.application.domain.ApplicationStatus;
import com.gongbang.application.repository.ApplicationRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 모집글 도메인이 부르는 지원 정리. 다른 도메인 service를 부르지 않는다: ApplicationCommandService는 모집글 service를 부르므로,
 * 모집글 삭제가 그쪽을 부르면 순환이 생긴다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ApplicationCloseService {

    private final ApplicationRepository applicationRepository;

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
}
