package com.gongbang.post.service;

import com.gongbang.application.service.ApplicationCloseService;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
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

    /**
     * memberId를 이 글 팀의 확정 팀원으로 넣는다. 첫 확정이면 팀을 만들고 작성자를 팀장으로 넣는다 (INT-006 ①). 확정 인원이 목표에 닿으면 자동
     * 모집완료하고 남은 대기 지원을 자동 거절한다 (REC-004 ⑤, REC-006 ①④).
     *
     * <p>모집완료 글이면 409 POST_COMPLETED, 정원이 찼으면 409 POST_FULL, 이미 현재 팀원이면 409 ALREADY_TEAM_MEMBER
     * (TeamService), 없거나 삭제된 글이면 404 POST_NOT_FOUND.
     */
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
        // TODO(확인 필요): 김건우 채팅이 생기면 확정 즉시 팀 채팅방에 넣는다 (INT-006 ②). 팀 이벤트 MEMBER_JOINED 발행도 같은 때
        // (TeamEventPublisher, 김건우 global).
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
        // TODO(확인 필요): 김건우 채팅이 생기면 여기서 팀 채팅방을 만든다 (INT-006 ①).
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
