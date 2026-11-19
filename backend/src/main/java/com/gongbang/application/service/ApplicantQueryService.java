package com.gongbang.application.service;

import com.gongbang.application.domain.ApplicationStatus;
import com.gongbang.application.dto.ApplicantListResponse;
import com.gongbang.application.dto.ApplicantListResponse.Counts;
import com.gongbang.application.dto.ApplicantListResponse.Item;
import com.gongbang.application.dto.ApplicantListResponse.MemberInfo;
import com.gongbang.application.repository.ApplicationRepository;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.service.MemberService;
import com.gongbang.post.domain.RecruitPost;
import com.gongbang.post.service.PostQueryService;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** REC-003 지원자 관리 모달의 목록. 모집글 도메인을 부르므로 ApplicationQueryService(모집글 상세가 부름)와 나눠 순환을 막는다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicantQueryService {

    // 지원자 관리 모달의 탭 (②). CLOSED는 삭제된 글의 지원이라 탭이 없다.
    static final Set<ApplicationStatus> TABS =
            Set.of(
                    ApplicationStatus.PENDING,
                    ApplicationStatus.ACCEPTED,
                    ApplicationStatus.REJECTED);

    private final ApplicationRepository applicationRepository;
    private final PostQueryService postQueryService;
    private final MemberService memberService;

    /**
     * REC-003 지원자 목록. 작성자만 본다 (403 NOT_POST_AUTHOR). 탭별 인원 수와 고른 탭의 지원자를 먼저 지원한 순으로 준다.
     *
     * @param memberId 로그인 회원 ID (인증 모듈에서 받는다)
     * @param status PENDING / ACCEPTED / REJECTED. null이면 PENDING (처음엔 대기 탭)
     */
    public ApplicantListResponse getApplicants(Long memberId, Long postId, String status) {
        ApplicationStatus tab = parseTab(status);
        RecruitPost post = postQueryService.getActivePost(postId);
        if (!post.getAuthorId().equals(memberId)) {
            throw new BusinessException(ErrorCode.NOT_POST_AUTHOR);
        }

        List<Item> items =
                applicationRepository
                        .findByPostIdAndStatusOrderByCreatedAtAscIdAsc(postId, tab)
                        .stream()
                        .map(
                                application ->
                                        Item.of(application, member(application.getApplicantId())))
                        .toList();
        return new ApplicantListResponse(counts(postId), items);
    }

    /**
     * 지원자 프로필 (③, INT-002 ①). 탈퇴·삭제 회원은 닉네임을 비워 화면이 「탈퇴한 사용자」로 보여 주게 한다. TODO(확인 필요): 회원 도메인(정진우)에
     * 여러 명을 한 번에 읽는 메서드가 생기면 바꾼다. 지금은 한 명씩 읽는다.
     */
    private MemberInfo member(Long memberId) {
        return memberService
                .findActiveMember(memberId)
                .map(
                        m ->
                                new MemberInfo(
                                        memberId,
                                        m.getNickname(),
                                        m.getProfileImageUrl(),
                                        m.getMajor(),
                                        m.getBio()))
                .orElseGet(() -> new MemberInfo(memberId, null, null, null, null));
    }

    private Counts counts(Long postId) {
        Map<ApplicationStatus, Long> byStatus = new EnumMap<>(ApplicationStatus.class);
        for (Object[] row : applicationRepository.countByStatus(postId)) {
            byStatus.put((ApplicationStatus) row[0], (Long) row[1]);
        }
        return new Counts(
                byStatus.getOrDefault(ApplicationStatus.PENDING, 0L),
                byStatus.getOrDefault(ApplicationStatus.ACCEPTED, 0L),
                byStatus.getOrDefault(ApplicationStatus.REJECTED, 0L));
    }

    private static ApplicationStatus parseTab(String status) {
        if (status == null) {
            return ApplicationStatus.PENDING;
        }
        try {
            ApplicationStatus tab = ApplicationStatus.valueOf(status);
            if (TABS.contains(tab)) {
                return tab;
            }
        } catch (IllegalArgumentException ignored) {
            // 아래에서 400
        }
        throw new BusinessException(ErrorCode.INVALID_INPUT);
    }
}
