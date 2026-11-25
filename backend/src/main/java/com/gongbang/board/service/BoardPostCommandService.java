package com.gongbang.board.service;

import com.gongbang.board.domain.BoardCategory;
import com.gongbang.board.domain.BoardPost;
import com.gongbang.board.dto.BoardPostCreateResponse;
import com.gongbang.board.dto.BoardPostDetailResponse;
import com.gongbang.board.dto.BoardPostLikeResponse;
import com.gongbang.board.dto.BoardPostRequest;
import com.gongbang.board.repository.BoardPostRepository;
import com.gongbang.contest.service.ContestLookupService;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.global.validation.UrlPolicy;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** BRD-002 작성, BRD-003 수정·삭제, BRD-007 좋아요. 권한과 값 검증은 여기서 한다 (AGENTS.md 0-2). */
@Service
@RequiredArgsConstructor
@Transactional
public class BoardPostCommandService {

    static final int TITLE_MAX = 50;
    static final int CONTENT_MAX = 2000;
    static final int URL_MAX = 500;

    // BRD-002 ④ 문구
    static final String CATEGORY_REQUIRED = "말머리를 선택해 주세요";
    static final String TITLE_REQUIRED = "제목을 입력해 주세요";
    static final String CONTENT_REQUIRED = "내용을 입력해 주세요";
    static final String URL_INVALID = "http:// 또는 https://로 시작하는 주소를 입력해 주세요";

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final BoardPostRepository boardPostRepository;
    private final ContestLookupService contestLookupService;
    private final BoardPostQueryService boardPostQueryService;
    private final BoardPostImageService boardPostImageService;
    private final com.gongbang.notification.service.CommunityNotificationService
            communityNotifications;

    /** BRD-002 ①②: 로그인 회원이 쓴다. 공모전 이야기는 공모전 필수, 결과물 링크는 결과물 자랑에서만 둔다. */
    public BoardPostCreateResponse create(Long memberId, BoardPostRequest request) {
        Values values = validate(request);
        BoardPost post =
                boardPostRepository.save(
                        BoardPost.of(
                                memberId,
                                values.category(),
                                values.contestId(),
                                values.title(),
                                values.content(),
                                values.linkUrl()));
        communityNotifications.postCreated(memberId, post.getId(), post.getTitle());
        return new BoardPostCreateResponse(post.getId());
    }

    /** BRD-003 ②③ 수정. 작성자만(403), 검사 규칙은 작성과 같다. 결과물 자랑에서 다른 말머리로 바꾸면 결과물 링크와 사진을 지운다. */
    public BoardPostDetailResponse update(
            Long memberId, Long boardPostId, BoardPostRequest request) {
        return update(memberId, boardPostId, request, LocalDateTime.now(KST));
    }

    BoardPostDetailResponse update(
            Long memberId, Long boardPostId, BoardPostRequest request, LocalDateTime now) {
        BoardPost post = lockOwnPost(memberId, boardPostId);
        Values values = validate(request);
        if (post.getCategory() == BoardCategory.SHOWCASE
                && values.category() != BoardCategory.SHOWCASE) {
            boardPostImageService.deleteAll(boardPostId, now);
        }
        post.update(
                values.category(),
                values.contestId(),
                values.title(),
                values.content(),
                values.linkUrl());
        return boardPostQueryService.describe(post, memberId);
    }

    /** BRD-003 ④ 삭제: 작성자만, 소프트 삭제. 올린 사진은 남긴다. */
    public void delete(Long memberId, Long boardPostId) {
        delete(memberId, boardPostId, LocalDateTime.now(KST));
    }

    void delete(Long memberId, Long boardPostId, LocalDateTime now) {
        lockOwnPost(memberId, boardPostId).delete(now);
    }

    /**
     * BRD-007 좋아요. 내 글에는 누를 수 없다(403). 이미 눌렀으면 그대로 성공한다. 게시글 행을 잠근 뒤 좋아요 행과 like_count를 함께 바꿔 동시에
     * 눌러도 숫자가 어긋나지 않는다.
     */
    public BoardPostLikeResponse like(Long memberId, Long boardPostId) {
        BoardPost post = lockActivePost(boardPostId);
        if (post.isWrittenBy(memberId)) {
            throw new BusinessException(ErrorCode.CANNOT_LIKE_OWN_BOARD_POST);
        }
        if (boardPostRepository.insertLike(boardPostId, memberId) == 1) {
            post.increaseLikeCount();
        }
        return new BoardPostLikeResponse(post.getLikeCount(), true);
    }

    /** BRD-007 좋아요 취소. 누르지 않았어도 그대로 성공한다. */
    public BoardPostLikeResponse unlike(Long memberId, Long boardPostId) {
        BoardPost post = lockActivePost(boardPostId);
        if (boardPostRepository.deleteLike(boardPostId, memberId) == 1) {
            post.decreaseLikeCount();
        }
        return new BoardPostLikeResponse(post.getLikeCount(), false);
    }

    private BoardPost lockActivePost(Long boardPostId) {
        return boardPostRepository
                .findByIdForUpdate(boardPostId)
                .filter(post -> !post.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_POST_NOT_FOUND));
    }

    private BoardPost lockOwnPost(Long memberId, Long boardPostId) {
        BoardPost post = lockActivePost(boardPostId);
        if (!post.isWrittenBy(memberId)) {
            throw new BusinessException(ErrorCode.NOT_BOARD_POST_AUTHOR);
        }
        return post;
    }

    private record Values(
            BoardCategory category, Long contestId, String title, String content, String linkUrl) {}

    /** BRD-002 ①②④ 검사. 제목·내용은 앞뒤 공백을 빼고 센다. */
    private Values validate(BoardPostRequest request) {
        if (request == null || request.category() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, CATEGORY_REQUIRED);
        }
        String title = request.title() == null ? "" : request.title().trim();
        if (title.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, TITLE_REQUIRED);
        }
        String content = request.content() == null ? "" : request.content().trim();
        if (content.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, CONTENT_REQUIRED);
        }
        if (title.length() > TITLE_MAX || content.length() > CONTENT_MAX) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        BoardCategory category = request.category();
        Long contestId = request.contestId();
        if (category == BoardCategory.CONTEST && contestId == null) {
            throw new BusinessException(ErrorCode.CONTEST_REQUIRED);
        }
        // ① 마감된 공모전도 고를 수 있다 (후기용). 없는 공모전만 막는다
        if (contestId != null && contestLookupService.findSummary(contestId).isEmpty()) {
            throw new BusinessException(ErrorCode.CONTEST_NOT_FOUND);
        }

        // ② 결과물 링크는 결과물 자랑에서만 둔다. 다른 말머리면 보내도 저장하지 않는다
        String linkUrl = category == BoardCategory.SHOWCASE ? optionalUrl(request.linkUrl()) : null;
        return new Values(category, contestId, title, content, linkUrl);
    }

    /**
     * 비었으면 null, 아니면 공통 URL 정책(#120, 대소문자 무관 http·https, 호스트 필수)에 맞아야 한다 (AGENTS.md 9, javascript:
     * 막기). 틀리면 BRD-002 ④ 문구.
     */
    private static String optionalUrl(String raw) {
        String url;
        try {
            url = UrlPolicy.validateOrNull(raw);
        } catch (BusinessException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, URL_INVALID);
        }
        if (url != null && url.length() > URL_MAX) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, URL_INVALID);
        }
        return url;
    }
}
