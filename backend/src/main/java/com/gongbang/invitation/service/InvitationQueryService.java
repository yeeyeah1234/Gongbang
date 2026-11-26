package com.gongbang.invitation.service;

import com.gongbang.invitation.domain.TeamInvitation;
import com.gongbang.invitation.dto.RecruitingPostResponse;
import com.gongbang.invitation.repository.TeamInvitationRepository;
import com.gongbang.post.service.PostQueryService;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 팀 초대 상태 일괄 조회(알림 NOTI-02 ④) 및 초대할 내 모집글 목록 조회(NET-02) 서비스. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvitationQueryService {

    private final TeamInvitationRepository teamInvitationRepository;
    private final PostQueryService postQueryService;

    /**
     * 여러 초대 ID의 현재 상태(`PENDING` · `ACCEPTED` · `DECLINED` · `CANCELED`)를 한 번에 조회한다.
     *
     * <p>없는 ID는 결과 맵에서 생략한다.
     */
    public Map<Long, String> findStatuses(Collection<Long> invitationIds) {
        if (invitationIds == null || invitationIds.isEmpty()) {
            return Map.of();
        }
        Set<Long> distinctIds =
                invitationIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (distinctIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> result = new LinkedHashMap<>();
        for (TeamInvitation invitation : teamInvitationRepository.findByIdIn(distinctIds)) {
            result.put(invitation.getId(), invitation.getStatus().name());
        }
        return result;
    }

    /**
     * 내가 작성한 모집중(`RECRUITING`, 미삭제) 모집글 목록을 최신순으로 반환한다 (`GET /api/members/me/recruiting-posts`).
     */
    public List<RecruitingPostResponse> getMyRecruitingPosts(Long memberId) {
        return postQueryService.getMyRecruitingPosts(memberId).stream()
                .map(RecruitingPostResponse::from)
                .toList();
    }
}
