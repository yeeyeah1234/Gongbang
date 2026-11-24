package com.gongbang.workspace.document.service;

import com.gongbang.member.dto.MemberProfileSummary;
import com.gongbang.member.service.MemberService;
import com.gongbang.team.service.TeamMemberService;
import com.gongbang.workspace.document.dto.DocumentListItemResponse;
import com.gongbang.workspace.document.repository.DocumentRepository;
import com.gongbang.workspace.document.repository.DocumentSummary;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 팀 문서 목록 (DOC-01). 생성·이름 변경·삭제는 {@link DocumentService}. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentQueryService {

    /** 회원 행이 없을 때의 표시 이름. MemberService의 탈퇴 표시와 같다. */
    static final String WITHDRAWN_NAME = "탈퇴한 사용자";

    private final DocumentRepository documentRepository;
    private final TeamMemberService teamMemberService;
    private final MemberService memberService;

    /** DOC-01 목록: 삭제되지 않은 문서, updated_at 최신순. 현재 팀원이 아니면 403 NOT_TEAM_MEMBER. */
    public List<DocumentListItemResponse> getDocuments(Long memberId, Long teamId) {
        teamMemberService.requireMember(teamId, memberId);
        List<DocumentSummary> summaries = documentRepository.findSummaries(teamId);
        if (summaries.isEmpty()) {
            return List.of();
        }

        boolean leader = teamMemberService.isLeader(teamId, memberId);
        Set<Long> editorIds =
                summaries.stream().map(DocumentSummary::updatedBy).collect(Collectors.toSet());
        Map<Long, MemberProfileSummary> editors = memberService.getProfileSummaries(editorIds);
        return summaries.stream()
                .map(
                        summary ->
                                DocumentListItemResponse.of(
                                        summary,
                                        nickname(editors.get(summary.updatedBy())),
                                        leader || Objects.equals(summary.createdBy(), memberId)))
                .toList();
    }

    private static String nickname(MemberProfileSummary profile) {
        return profile == null || profile.nickname() == null ? WITHDRAWN_NAME : profile.nickname();
    }
}
