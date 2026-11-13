package com.gongbang.post.service;

import com.gongbang.contest.service.ContestDetailService;
import com.gongbang.contest.service.ContestQueryService;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.post.domain.ExternalContest;
import com.gongbang.post.domain.RecruitPost;
import com.gongbang.post.dto.PostCreateRequest;
import com.gongbang.post.dto.PostCreateResponse;
import com.gongbang.post.repository.RecruitPostRepository;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 모집글 작성·수정·삭제. */
@Service
@RequiredArgsConstructor
@Transactional
public class PostCommandService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    // 기능정의서 PST-002 ①②의 범위
    static final int TITLE_MAX_LENGTH = 50;
    static final int CONTENT_MAX_LENGTH = 2000;
    static final int EXTERNAL_TITLE_MAX_LENGTH = 50;
    static final int TARGET_COUNT_MIN = 2;
    static final int TARGET_COUNT_MAX = 8;
    // DB 명세서 related_url·ext_contest_url VARCHAR(500)
    static final int URL_MAX_LENGTH = 500;

    private final RecruitPostRepository recruitPostRepository;
    private final ContestDetailService contestDetailService;
    private final ContestQueryService contestQueryService;

    /**
     * PST-002 모집글 작성. 작성자가 확정 인원 1명인 「모집중」 글을 만든다. 팀은 첫 수락 때 만든다 (INT-006).
     *
     * @param memberId 로그인 회원 ID (인증 모듈에서 받는다)
     */
    public PostCreateResponse create(Long memberId, PostCreateRequest request) {
        return create(memberId, request, LocalDate.now(KST));
    }

    PostCreateResponse create(Long memberId, PostCreateRequest request, LocalDate today) {
        if (request.contestId() != null && request.external() != null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (request.contestId() == null && request.external() == null) {
            throw new BusinessException(ErrorCode.CONTEST_REQUIRED);
        }
        String title = requireText(request.title(), TITLE_MAX_LENGTH);
        String content = requireText(request.content(), CONTENT_MAX_LENGTH);
        int targetCount = requireTargetCount(request.targetCount());
        String relatedUrl = optionalUrl(request.relatedUrl());
        LocalDate startDate = require(request.recruitStartDate());
        LocalDate endDate = require(request.recruitEndDate());

        RecruitPost post;
        if (request.contestId() != null) {
            LocalDate deadline =
                    contestDetailService
                            .findEndDate(request.contestId())
                            .orElseThrow(() -> new BusinessException(ErrorCode.CONTEST_NOT_FOUND));
            validatePeriod(startDate, endDate, deadline, today);
            post =
                    RecruitPost.withContest(
                            memberId,
                            request.contestId(),
                            title,
                            content,
                            targetCount,
                            startDate,
                            endDate,
                            relatedUrl);
        } else {
            ExternalContest externalContest = toExternalContest(request.external());
            validatePeriod(startDate, endDate, externalContest.getDeadline(), today);
            post =
                    RecruitPost.withExternalContest(
                            memberId,
                            externalContest,
                            title,
                            content,
                            targetCount,
                            startDate,
                            endDate,
                            relatedUrl);
        }
        return new PostCreateResponse(recruitPostRepository.save(post).getId());
    }

    private ExternalContest toExternalContest(PostCreateRequest.External external) {
        String title = requireText(external.title(), EXTERNAL_TITLE_MAX_LENGTH);
        if (!StringUtils.hasText(external.categoryCode())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        Long categoryId =
                contestQueryService
                        .findCategoryId(external.categoryCode().trim())
                        .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT));
        LocalDate deadline = require(external.deadline());
        String url = optionalUrl(external.url());
        // TODO(확인 필요): 공모전 링크의 대표 이미지(og:image) 불러오기는 라이브러리·SSRF 방어를 정한 뒤 별도 PR로 붙인다
        // (PST-002 ⑤). 그 전에는 null로 두고 화면은 기본 이미지를 쓴다.
        return ExternalContest.of(title, categoryId, deadline, url);
    }

    /** PST-002 ②③: 마감된 공모전은 막고, 시작일 ≤ 종료일 ≤ 공모전 마감일이어야 한다. */
    private void validatePeriod(
            LocalDate startDate, LocalDate endDate, LocalDate deadline, LocalDate today) {
        if (deadline.isBefore(today)) {
            throw new BusinessException(ErrorCode.CONTEST_ENDED);
        }
        if (endDate.isBefore(startDate) || endDate.isAfter(deadline)) {
            throw new BusinessException(ErrorCode.INVALID_PERIOD);
        }
    }

    /** 앞뒤 공백을 뺀 값이 1자 이상, max자 이하인지 본다. 글자 수는 DB(utf8mb4)처럼 문자 단위로 센다. */
    private static String requireText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        String stripped = value.strip();
        if (stripped.codePointCount(0, stripped.length()) > maxLength) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return stripped;
    }

    private static int requireTargetCount(Integer targetCount) {
        if (targetCount == null || targetCount < TARGET_COUNT_MIN || targetCount > TARGET_COUNT_MAX) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return targetCount;
    }

    private static <T> T require(T value) {
        if (value == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return value;
    }

    /** PST-002 ④: 빈 값이면 null, 있으면 http:// 또는 https://로 시작하는 주소만 받는다 (javascript: 등 차단). */
    private static String optionalUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String url = value.strip();
        String lower = url.toLowerCase(Locale.ROOT);
        boolean httpScheme = lower.startsWith("http://") || lower.startsWith("https://");
        if (!httpScheme || url.length() > URL_MAX_LENGTH || !hasHost(url)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return url;
    }

    private static boolean hasHost(String url) {
        try {
            return new URI(url).getHost() != null;
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
