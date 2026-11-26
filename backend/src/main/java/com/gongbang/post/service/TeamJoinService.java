package com.gongbang.post.service;

import com.gongbang.application.service.ApplicationCloseService;
import com.gongbang.chat.service.ChatMembershipService;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.global.websocket.TeamEventPublisher;
import com.gongbang.post.domain.RecruitPost;
import com.gongbang.post.dto.TeamJoinResult;
import com.gongbang.team.service.TeamService;
import com.gongbang.workspace.calendar.service.CalendarService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * INT-005 확정 팀원 등록 공통 처리 (API 명세서 TeamJoinService.join). 지원 수락(REC-004)과 팀 초대 수락(김건우)이 같이 쓴다.
 *
 * <p>모집글 행을 잠근 뒤(SELECT … FOR UPDATE) 정원을 확인하고 올린다 (AGENTS.md 7). 잠금 순서는 recruit_post →
 * team(TeamService) → application. 부르는 쪽 트랜잭션에 참여한다.
 *
 * <p><b>부르는 쪽 주의</b>: 트랜잭션을 READ COMMITTED로 시작하거나, join 전에 일반 조회(잠그지 않는 SELECT)를 하지 않는다. MySQL
 * 기본(REPEATABLE READ)에서 잠금 전에 일반 조회를 하면 그 순간으로 읽기 시점이 고정되어, 잠금을 기다리는 사이 다른 요청이 만든 팀을 보지 못하고 팀을 또
 * 만들려다 실패한다 (지원 수락 REC-004는 READ COMMITTED, 팀 초대 수락도 같게 해야 한다).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TeamJoinService {

    // DB 명세서 team.name: 「팀 생성 때 모집글 제목 앞 20자로 채움」
    static final int TEAM_NAME_MAX_LENGTH = 20;

    private final PostCommandService postCommandService;
    private final TeamService teamService;
    private final ApplicationCloseService applicationCloseService;
    private final RecruitNotifier recruitNotifier;
    private final CalendarService calendarService;
    private final ChatMembershipService chatMembershipService;
    private final TeamEventPublisher teamEventPublisher;

    /**
     * memberId를 이 글 팀의 확정 팀원으로 넣는다. 첫 확정이면 팀을 만들고 작성자를 팀장으로 넣는다 (INT-006 ①). 확정 인원이 목표에 닿으면 자동
     * 모집완료하고 남은 대기 지원을 자동 거절한다 (REC-004 ⑤, REC-006 ①④).
     *
     * <p>모집완료 글이면 409 POST_COMPLETED, 정원이 찼으면 409 POST_FULL, 이미 현재 팀원이면 409 ALREADY_TEAM_MEMBER
     * (TeamService), 없거나 삭제된 글이면 404 POST_NOT_FOUND.
     */
    /**
     * INT-005 팀 초대 수락 (김건우 초대 수락 서비스가 부른다). join과 같은 정원·모집완료 규칙으로 확정 팀원을 등록하고, 그 회원이 이 글에 대기 중인 지원이
     * 있으면 「수락」으로 바꾼다 (③). 수락 알림(INT-003 ②)은 보내지 않는다: 초대를 수락한 사람이 본인이기 때문이다 (NOTI-04 ②).
     *
     * <p>잠금 순서를 지원 수락(REC-004)과 같게 모집글 → 지원으로 지킨다. 대기 지원을 join보다 먼저 수락으로 바꿔, 이 수락으로 정원이 차서 남은 대기
     * 지원을 자동 거절할 때 이 회원의 지원이 거절되지 않게 한다. join이 실패하면(409 POST_FULL 등) 부르는 쪽 트랜잭션과 함께 지원 변경도 되돌아간다.
     *
     * <p>부르는 쪽은 join과 같이 트랜잭션을 READ COMMITTED로 시작한다 (클래스 설명 참고).
     */
    public TeamJoinResult joinByInvitation(Long postId, Long memberId, LocalDateTime now) {
        postCommandService.lockPost(postId);
        applicationCloseService.acceptPendingApplication(postId, memberId, now);
        return join(postId, memberId, now);
    }

    public TeamJoinResult join(Long postId, Long memberId, LocalDateTime now) {
        RecruitPost post = postCommandService.lockPost(postId);
        if (!post.isRecruiting()) {
            throw new BusinessException(ErrorCode.POST_COMPLETED);
        }
        if (post.isFull()) {
            throw new BusinessException(ErrorCode.POST_FULL);
        }

        Long teamId = teamService.findTeamIdByPostId(postId).orElseGet(() -> createTeam(post));
        teamService.addMember(teamId, memberId);
        chatMembershipService.addMemberToTeamRoom(teamId, memberId);
        teamEventPublisher.publish(teamId, "MEMBER_JOINED", memberId, memberId);
        post.addConfirmedMember();

        if (post.isFull()) {
            post.complete(now);
            List<Long> rejectedApplicantIds =
                    applicationCloseService.autoRejectPendingApplications(postId, now);
            rejectedApplicantIds.forEach(
                    applicantId -> recruitNotifier.rejected(post, applicantId, null));
        }
        return TeamJoinResult.of(teamId, post);
    }

    /** INT-006 ①: 첫 확정 때 팀을 만들고 작성자를 팀장으로 넣는다. */
    private Long createTeam(RecruitPost post) {
        Long teamId =
                teamService.createTeam(post.getId(), teamName(post.getTitle()), post.getAuthorId());
        chatMembershipService.createTeamRoom(teamId, post.getAuthorId());
        // CAL-06: 공모전 마감 자동 일정. 이 트랜잭션에 합류하고, 마감일이 없으면 아무것도 하지 않는다 (예외 없음)
        calendarService.upsertAutoEvent(teamId);
        return teamId;
    }

    /** 모집글 제목 앞 20자(문자 단위). 제목은 작성 때 앞뒤 공백을 지워 1~50자로 저장되어 있다. */
    static String teamName(String title) {
        if (title.codePointCount(0, title.length()) <= TEAM_NAME_MAX_LENGTH) {
            return title;
        }
        int end = title.offsetByCodePoints(0, TEAM_NAME_MAX_LENGTH);
        return title.substring(0, end).strip();
    }
}
