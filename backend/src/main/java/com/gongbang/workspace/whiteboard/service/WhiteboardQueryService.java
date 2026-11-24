package com.gongbang.workspace.whiteboard.service;

import com.gongbang.member.dto.MemberProfileSummary;
import com.gongbang.member.service.MemberService;
import com.gongbang.team.service.TeamMemberService;
import com.gongbang.workspace.whiteboard.dto.WhiteboardListItemResponse;
import com.gongbang.workspace.whiteboard.dto.WhiteboardListItemResponse.LastEditor;
import com.gongbang.workspace.whiteboard.repository.WhiteboardRepository;
import com.gongbang.workspace.whiteboard.repository.WhiteboardSummary;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 팀 화이트보드 목록 (WB-01). 만들기·제목 변경·삭제는 {@link WhiteboardService}. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WhiteboardQueryService {

    private final WhiteboardRepository whiteboardRepository;
    private final TeamMemberService teamMemberService;
    private final MemberService memberService;

    /** WB-01 목록: 삭제되지 않은 보드, updated_at 최신순. 현재 팀원이 아니면 403 NOT_TEAM_MEMBER. */
    public List<WhiteboardListItemResponse> getWhiteboards(Long memberId, Long teamId) {
        teamMemberService.requireMember(teamId, memberId);
        List<WhiteboardSummary> summaries = whiteboardRepository.findSummaries(teamId);
        if (summaries.isEmpty()) {
            return List.of();
        }

        Set<Long> editorIds =
                summaries.stream()
                        .map(WhiteboardSummary::lastEditorId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
        Map<Long, MemberProfileSummary> editors =
                editorIds.isEmpty() ? Map.of() : memberService.getProfileSummaries(editorIds);
        return summaries.stream()
                .map(
                        summary ->
                                WhiteboardListItemResponse.of(
                                        summary, lastEditor(summary, editors)))
                .toList();
    }

    private static LastEditor lastEditor(
            WhiteboardSummary summary, Map<Long, MemberProfileSummary> editors) {
        Long editorId = summary.lastEditorId();
        if (editorId == null) {
            return null;
        }
        // 탈퇴·삭제된 회원도 MemberService가 「탈퇴한 사용자」로 채워 준다 (#120에서 각자 넣던 문자열을 뺌)
        return new LastEditor(editorId, editors.get(editorId).nickname());
    }
}
