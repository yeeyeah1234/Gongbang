package com.gongbang.member.dto;

import com.gongbang.member.domain.DesiredRole;
import com.gongbang.member.domain.Grade;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * API 명세서 MY-01 PATCH /api/members/me. 보내지 않은(null) 칸은 그대로 둔다. TODO(확인 필요): 명세서는
 * profileImageFileId(/api/files로 먼저 올림)인데 파일 업로드 모듈(uploaded_file)이 아직 없어 profileImageUrl을 받는다. 업로드
 * 모듈이 생기면 바꾼다.
 */
public record UpdateProfileRequest(
        String nickname,
        String profileImageUrl,
        Boolean removeProfileImage,
        @Size(max = 50) String bio,
        @Size(max = 50) String school,
        @Size(max = 50) String major,
        Grade grade,
        List<String> interestCategoryCodes,
        List<DesiredRole> desiredRoles,
        List<String> skillTags,
        @Size(max = 500) String portfolioUrl) {}
