package com.gongbang.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gongbang.application.service.ApplicationCloseService;
import com.gongbang.contest.service.ContestDetailService;
import com.gongbang.contest.service.ContestQueryService;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.post.domain.ExternalContest;
import com.gongbang.post.domain.PostStatus;
import com.gongbang.post.domain.RecruitPost;
import com.gongbang.post.dto.PostUpdateRequest;
import com.gongbang.post.repository.RecruitPostRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** PST-003 모집글 수정 (PostCommandService.update). */
@ExtendWith(MockitoExtension.class)
class PostUpdateServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 13);
    private static final LocalDate DEADLINE = LocalDate.of(2026, 12, 31);
    private static final Long POST_ID = 10L;
    private static final Long AUTHOR_ID = 7L;
    private static final Long CONTEST_ID = 3L;

    @Mock private RecruitPostRepository recruitPostRepository;
    @Mock private ContestDetailService contestDetailService;
    @Mock private ContestQueryService contestQueryService;
    @Mock private PostDetailService postDetailService;
    @Mock private ApplicationCloseService applicationCloseService;
    @Mock private RecruitNotifier recruitNotifier;

    private PostCommandService postCommandService;

    @BeforeEach
    void setUp() {
        postCommandService =
                new PostCommandService(
                        recruitPostRepository,
                        contestDetailService,
                        contestQueryService,
                        postDetailService,
                        applicationCloseService,
                        recruitNotifier);
    }

    /** 확정 인원 currentCount명, 목표 4명, 모집 기간 오늘~12/1인 서비스 공모전 글. */
    private RecruitPost givenPost(int currentCount, PostStatus status) {
        RecruitPost post =
                RecruitPost.withContest(
                        AUTHOR_ID,
                        CONTEST_ID,
                        "원래 제목",
                        "원래 내용",
                        4,
                        TODAY,
                        LocalDate.of(2026, 12, 1),
                        "https://old.example.com");
        ReflectionTestUtils.setField(post, "id", POST_ID);
        ReflectionTestUtils.setField(post, "currentCount", currentCount);
        ReflectionTestUtils.setField(post, "status", status);
        given(recruitPostRepository.findByIdForUpdate(POST_ID)).willReturn(Optional.of(post));
        return post;
    }

    private static PostUpdateRequest targetOnly(int targetCount) {
        return new PostUpdateRequest(null, targetCount, null, null, null, null);
    }

    private ErrorCode errorOf(Long memberId, PostUpdateRequest request) {
        return assertThrows(
                        BusinessException.class,
                        () -> postCommandService.update(memberId, POST_ID, request, TODAY))
                .getErrorCode();
    }

    @Test
    @DisplayName("보낸 값만 고치고, 보내지 않은 값과 연결 공모전은 그대로 둔다. 응답은 조회수 없이 만든 상세")
    void 보낸_값만_고친다() {
        RecruitPost post = givenPost(1, PostStatus.RECRUITING);

        postCommandService.update(
                AUTHOR_ID,
                POST_ID,
                new PostUpdateRequest(" 새 제목 ", null, null, null, null, "새 내용"),
                TODAY);

        assertThat(post.getTitle()).isEqualTo("새 제목");
        assertThat(post.getContent()).isEqualTo("새 내용");
        assertThat(post.getTargetCount()).isEqualTo(4);
        assertThat(post.getRelatedUrl()).isEqualTo("https://old.example.com");
        assertThat(post.getContestId()).isEqualTo(CONTEST_ID);
        verify(postDetailService).describe(post, AUTHOR_ID, TODAY);
    }

    @Test
    @DisplayName("관련 URL을 빈 문자열로 보내면 지운다")
    void 관련_URL을_비우면_지운다() {
        RecruitPost post = givenPost(1, PostStatus.RECRUITING);

        postCommandService.update(
                AUTHOR_ID, POST_ID, new PostUpdateRequest(null, null, null, null, "", null), TODAY);

        assertThat(post.getRelatedUrl()).isNull();
    }

    @Test
    @DisplayName("작성자가 아니면 403 NOT_POST_AUTHOR이고 아무것도 바꾸지 않는다")
    void 작성자가_아니면_403() {
        RecruitPost post = givenPost(1, PostStatus.RECRUITING);

        assertThat(errorOf(99L, new PostUpdateRequest("남의 글", null, null, null, null, null)))
                .isEqualTo(ErrorCode.NOT_POST_AUTHOR);
        assertThat(post.getTitle()).isEqualTo("원래 제목");
    }

    @Test
    @DisplayName("없거나 삭제된 글이면 404 POST_NOT_FOUND")
    void 없거나_삭제된_글이면_404() {
        given(recruitPostRepository.findByIdForUpdate(POST_ID)).willReturn(Optional.empty());
        assertThat(errorOf(AUTHOR_ID, targetOnly(5))).isEqualTo(ErrorCode.POST_NOT_FOUND);

        RecruitPost deleted = givenPost(1, PostStatus.RECRUITING);
        ReflectionTestUtils.setField(deleted, "deletedAt", LocalDateTime.of(2026, 11, 12, 9, 0));
        assertThat(errorOf(AUTHOR_ID, targetOnly(5))).isEqualTo(ErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("목표 인원을 확정 인원보다 적게 하면 400 TARGET_BELOW_CURRENT")
    void 목표_인원이_확정_인원보다_적으면_400() {
        givenPost(3, PostStatus.RECRUITING);

        assertThat(errorOf(AUTHOR_ID, targetOnly(2))).isEqualTo(ErrorCode.TARGET_BELOW_CURRENT);
    }

    @Test
    @DisplayName("모집중인 글에서 목표 인원을 확정 인원과 같게 하면 400 TARGET_EQUALS_CURRENT")
    void 모집중_글은_확정_인원과_같게_할_수_없다() {
        givenPost(3, PostStatus.RECRUITING);

        assertThat(errorOf(AUTHOR_ID, targetOnly(3))).isEqualTo(ErrorCode.TARGET_EQUALS_CURRENT);
    }

    @Test
    @DisplayName("모집완료 글은 목표 인원을 확정 인원과 같게 할 수 있다")
    void 모집완료_글은_확정_인원과_같게_할_수_있다() {
        RecruitPost post = givenPost(3, PostStatus.COMPLETED);

        postCommandService.update(AUTHOR_ID, POST_ID, targetOnly(3), TODAY);

        assertThat(post.getTargetCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("모집완료 글에서 목표 인원을 늘려도 모집완료 그대로다")
    void 모집완료_글은_인원을_늘려도_모집완료() {
        RecruitPost post = givenPost(4, PostStatus.COMPLETED);

        postCommandService.update(AUTHOR_ID, POST_ID, targetOnly(6), TODAY);

        assertThat(post.getTargetCount()).isEqualTo(6);
        assertThat(post.getStatus()).isEqualTo(PostStatus.COMPLETED);
    }

    @Test
    @DisplayName("목표 인원이 2~8명 밖이면 400 INVALID_INPUT")
    void 목표_인원이_범위_밖이면_400() {
        givenPost(1, PostStatus.RECRUITING);

        assertThat(errorOf(AUTHOR_ID, targetOnly(9))).isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("종료일만 바꿔도 기존 시작일과 공모전 마감일로 기간을 검사한다")
    void 종료일이_공모전_마감일보다_뒤면_INVALID_PERIOD() {
        givenPost(1, PostStatus.RECRUITING);
        given(contestDetailService.findEndDate(CONTEST_ID)).willReturn(Optional.of(DEADLINE));

        assertThat(
                        errorOf(
                                AUTHOR_ID,
                                new PostUpdateRequest(
                                        null, null, null, DEADLINE.plusDays(1), null, null)))
                .isEqualTo(ErrorCode.INVALID_PERIOD);
    }

    @Test
    @DisplayName("공모전이 이미 마감됐어도 기간을 바꾸지 않으면 다른 값은 고칠 수 있다")
    void 마감된_공모전_글도_제목은_고칠_수_있다() {
        RecruitPost post = givenPost(1, PostStatus.RECRUITING);

        postCommandService.update(
                AUTHOR_ID,
                POST_ID,
                new PostUpdateRequest("제목만", null, null, null, null, null),
                DEADLINE.plusDays(10));

        assertThat(post.getTitle()).isEqualTo("제목만");
        verify(contestDetailService, never()).findEndDate(any());
    }

    @Test
    @DisplayName("외부 공모전 글은 입력한 접수 마감일로 기간을 검사한다")
    void 외부_공모전_글은_입력한_마감일로_검사한다() {
        RecruitPost post =
                RecruitPost.withExternalContest(
                        AUTHOR_ID,
                        ExternalContest.of("외부", 5L, LocalDate.of(2026, 11, 30), null),
                        "제목",
                        "내용",
                        4,
                        TODAY,
                        TODAY.plusDays(3),
                        null);
        given(recruitPostRepository.findByIdForUpdate(POST_ID)).willReturn(Optional.of(post));

        assertThat(
                        errorOf(
                                AUTHOR_ID,
                                new PostUpdateRequest(
                                        null, null, null, LocalDate.of(2026, 12, 1), null, null)))
                .isEqualTo(ErrorCode.INVALID_PERIOD);
        verify(contestDetailService, never()).findEndDate(any());
    }
}
