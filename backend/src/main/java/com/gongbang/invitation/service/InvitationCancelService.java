package com.gongbang.invitation.service;

import com.gongbang.invitation.domain.InvitationStatus;
import com.gongbang.invitation.domain.TeamInvitation;
import com.gongbang.invitation.repository.TeamInvitationRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 모집글 삭제(PST-004 ④)·관리자 숨김(AdminPostService)·모집완료(REC-006) 시 대기 중인 팀 초대를 일괄 취소하는 전용 서비스.
 *
 * <p>`PostCommandService → InvitationCommandService → TeamJoinService → PostCommandService` 순환 참조를
 * 막기 위해 {@link TeamInvitationRepository}만 의존한다.
 */
@Service
@RequiredArgsConstructor
public class InvitationCancelService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final TeamInvitationRepository teamInvitationRepository;

    /**
     * 해당 모집글의 대기(`PENDING`) 초대를 모두 `CANCELED`로 바꾸고 취소된 초대 대상자(`inviteeId`) 목록을 돌려준다.
     *
     * <p>대기 초대가 없거나 이미 취소된 상태에서 반복 호출해도 안전(멱등)하며, 부르는 쪽 트랜잭션에 참여한다.
     */
    @Transactional
    public List<Long> cancelPendingByPostId(Long postId, LocalDateTime now) {
        if (postId == null) {
            return List.of();
        }
        LocalDateTime canceledAt = now != null ? now : LocalDateTime.now(KST);
        List<TeamInvitation> pending =
                teamInvitationRepository.findByPostIdAndStatusForUpdate(
                        postId, InvitationStatus.PENDING);
        for (TeamInvitation invitation : pending) {
            invitation.cancel(canceledAt);
        }
        return pending.stream().map(TeamInvitation::getInviteeId).toList();
    }
}
