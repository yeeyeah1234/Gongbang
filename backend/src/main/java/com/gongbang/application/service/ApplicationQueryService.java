package com.gongbang.application.service;

import com.gongbang.application.dto.MyApplication;
import com.gongbang.application.repository.ApplicationRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 지원 조회. 모집글 도메인(상세의 myStatus)이 부른다. 다른 도메인 service를 부르지 않는다 (순환 방지). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicationQueryService {

    private final ApplicationRepository applicationRepository;

    /** 이 회원이 이 글에 낸 지원. 없으면 빈 값. */
    public Optional<MyApplication> findMyApplication(Long postId, Long memberId) {
        return applicationRepository
                .findByPostIdAndApplicantId(postId, memberId)
                .map(MyApplication::from);
    }
}
