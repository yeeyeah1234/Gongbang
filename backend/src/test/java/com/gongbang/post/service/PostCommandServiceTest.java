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
import com.gongbang.post.domain.PostStatus;
import com.gongbang.post.domain.RecruitPost;
import com.gongbang.post.dto.PostCreateRequest;
import com.gongbang.post.dto.PostCreateResponse;
import com.gongbang.post.repository.RecruitPostRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PostCommandServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 13);
    private static final LocalDate DEADLINE = LocalDate.of(2026, 12, 31);
    private static final Long AUTHOR_ID = 7L;
    private static final Long CONTEST_ID = 3L;

    @Mock private RecruitPostRepository recruitPostRepository;
    @Mock private ContestDetailService contestDetailService;
    @Mock private ContestQueryService contestQueryService;
    @Mock private PostDetailService postDetailService;
    @Mock private ApplicationCloseService applicationCloseService;

    private PostCommandService postCommandService;

    @BeforeEach
    void setUp() {
        postCommandService =
                new PostCommandService(
                        recruitPostRepository,
                        contestDetailService,
                        contestQueryService,
                        postDetailService,
                        applicationCloseService);
    }

    private static PostCreateRequest contestRequest(
            String title, Integer targetCount, LocalDate start, LocalDate end, String relatedUrl) {
        return new PostCreateRequest(
                CONTEST_ID, null, title, targetCount, start, end, relatedUrl, "같이 하실 분 구해요");
    }

    private static PostCreateRequest validContestRequest() {
        return contestRequest("AI 공모전 팀원 모집", 4, TODAY, TODAY.plusDays(14), null);
    }

    private static PostCreateRequest externalRequest(LocalDate deadline, String url) {
        return new PostCreateRequest(
                null,
                new PostCreateRequest.External(" 외부 해커톤 ", "IT_SW", deadline, url),
                "해커톤 같이 나가요",
                3,
                TODAY,
                TODAY.plusDays(7),
                null,
                "백엔드 한 분 구합니다");
    }

    private void givenContestDeadline(LocalDate deadline) {
        given(contestDetailService.findEndDate(CONTEST_ID)).willReturn(Optional.of(deadline));
    }

    private void givenSaveReturnsId(Long postId) {
        given(recruitPostRepository.save(any(RecruitPost.class)))
                .willAnswer(
                        invocation -> {
                            RecruitPost post = invocation.getArgument(0);
                            ReflectionTestUtils.setField(post, "id", postId);
                            return post;
                        });
    }

    private RecruitPost savedPost() {
        ArgumentCaptor<RecruitPost> captor = ArgumentCaptor.forClass(RecruitPost.class);
        verify(recruitPostRepository).save(captor.capture());
        return captor.getValue();
    }

    private ErrorCode errorOf(PostCreateRequest request) {
        BusinessException e =
                assertThrows(
                        BusinessException.class,
                        () -> postCommandService.create(AUTHOR_ID, request, TODAY));
        return e.getErrorCode();
    }

    @Test
    @DisplayName("서비스 공모전으로 쓰면 작성자 1명인 모집중 글로 저장하고 postId를 돌려준다")
    void 서비스_공모전으로_작성하면_모집중_1명으로_저장된다() {
        givenContestDeadline(DEADLINE);
        givenSaveReturnsId(10L);

        PostCreateResponse response =
                postCommandService.create(AUTHOR_ID, validContestRequest(), TODAY);

        assertThat(response.postId()).isEqualTo(10L);
        RecruitPost post = savedPost();
        assertThat(post.getAuthorId()).isEqualTo(AUTHOR_ID);
        assertThat(post.getContestId()).isEqualTo(CONTEST_ID);
        assertThat(post.getExternalContest()).isNull();
        assertThat(post.getCurrentCount()).isEqualTo(1);
        assertThat(post.getTargetCount()).isEqualTo(4);
        assertThat(post.getStatus()).isEqualTo(PostStatus.RECRUITING);
        assertThat(post.getViewCount()).isZero();
    }

    @Test
    @DisplayName("외부 공모전으로 쓰면 분야 코드를 ID로 바꿔 저장하고 대표 이미지는 비워 둔다")
    void 외부_공모전으로_작성하면_분야_ID와_함께_저장된다() {
        given(contestQueryService.findCategoryId("IT_SW")).willReturn(Optional.of(1L));
        givenSaveReturnsId(11L);

        postCommandService.create(
                AUTHOR_ID, externalRequest(DEADLINE, "https://example.com/hack"), TODAY);

        RecruitPost post = savedPost();
        assertThat(post.getContestId()).isNull();
        assertThat(post.getExternalContest().getTitle()).isEqualTo("외부 해커톤");
        assertThat(post.getExternalContest().getCategoryId()).isEqualTo(1L);
        assertThat(post.getExternalContest().getDeadline()).isEqualTo(DEADLINE);
        assertThat(post.getExternalContest().getUrl()).isEqualTo("https://example.com/hack");
        assertThat(post.getExternalContest().getImageUrl()).isNull();
    }

    @Test
    @DisplayName("공모전을 고르지 않으면 400 CONTEST_REQUIRED")
    void 공모전이_없으면_CONTEST_REQUIRED() {
        PostCreateRequest request =
                new PostCreateRequest(
                        null, null, "제목", 4, TODAY, TODAY.plusDays(1), null, "내용");

        assertThat(errorOf(request)).isEqualTo(ErrorCode.CONTEST_REQUIRED);
        verify(recruitPostRepository, never()).save(any());
    }

    @Test
    @DisplayName("서비스 공모전과 외부 공모전을 함께 보내면 400 INVALID_INPUT")
    void 공모전을_둘_다_보내면_INVALID_INPUT() {
        PostCreateRequest external = externalRequest(DEADLINE, null);
        PostCreateRequest request =
                new PostCreateRequest(
                        CONTEST_ID,
                        external.external(),
                        "제목",
                        4,
                        TODAY,
                        TODAY.plusDays(1),
                        null,
                        "내용");

        assertThat(errorOf(request)).isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("없는 서비스 공모전이면 404 CONTEST_NOT_FOUND")
    void 없는_공모전이면_CONTEST_NOT_FOUND() {
        given(contestDetailService.findEndDate(CONTEST_ID)).willReturn(Optional.empty());

        assertThat(errorOf(validContestRequest())).isEqualTo(ErrorCode.CONTEST_NOT_FOUND);
    }

    @Test
    @DisplayName("마감일이 어제인 서비스 공모전이면 400 CONTEST_ENDED")
    void 마감된_서비스_공모전이면_CONTEST_ENDED() {
        givenContestDeadline(TODAY.minusDays(1));
        PostCreateRequest request =
                contestRequest("제목", 4, TODAY.minusDays(3), TODAY.minusDays(1), null);

        assertThat(errorOf(request)).isEqualTo(ErrorCode.CONTEST_ENDED);
    }

    @Test
    @DisplayName("외부 공모전 마감일이 어제면 400 CONTEST_ENDED")
    void 마감된_외부_공모전이면_CONTEST_ENDED() {
        given(contestQueryService.findCategoryId("IT_SW")).willReturn(Optional.of(1L));

        assertThat(errorOf(externalRequest(TODAY.minusDays(1), null)))
                .isEqualTo(ErrorCode.CONTEST_ENDED);
    }

    @Test
    @DisplayName("공모전 마감일 당일에는 마감일까지 모집하는 글을 쓸 수 있다")
    void 마감일_당일에는_작성할_수_있다() {
        givenContestDeadline(TODAY);
        givenSaveReturnsId(12L);

        postCommandService.create(
                AUTHOR_ID, contestRequest("제목", 4, TODAY, TODAY, null), TODAY);

        assertThat(savedPost().getRecruitEndDate()).isEqualTo(TODAY);
    }

    @Test
    @DisplayName("종료일이 시작일보다 앞서면 400 INVALID_PERIOD")
    void 종료일이_시작일보다_앞서면_INVALID_PERIOD() {
        givenContestDeadline(DEADLINE);
        PostCreateRequest request =
                contestRequest("제목", 4, TODAY.plusDays(5), TODAY.plusDays(4), null);

        assertThat(errorOf(request)).isEqualTo(ErrorCode.INVALID_PERIOD);
    }

    @Test
    @DisplayName("종료일이 공모전 마감일보다 뒤면 400 INVALID_PERIOD")
    void 종료일이_공모전_마감일보다_뒤면_INVALID_PERIOD() {
        givenContestDeadline(DEADLINE);
        PostCreateRequest request =
                contestRequest("제목", 4, TODAY, DEADLINE.plusDays(1), null);

        assertThat(errorOf(request)).isEqualTo(ErrorCode.INVALID_PERIOD);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 9})
    @DisplayName("목표 인원이 2~8명 밖이면 400 INVALID_INPUT")
    void 목표_인원이_범위_밖이면_INVALID_INPUT(int targetCount) {
        PostCreateRequest request =
                contestRequest("제목", targetCount, TODAY, TODAY.plusDays(1), null);

        assertThat(errorOf(request)).isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 8})
    @DisplayName("목표 인원 2명과 8명은 받는다")
    void 목표_인원_경계값은_받는다(int targetCount) {
        givenContestDeadline(DEADLINE);
        givenSaveReturnsId(13L);

        postCommandService.create(
                AUTHOR_ID,
                contestRequest("제목", targetCount, TODAY, TODAY.plusDays(1), null),
                TODAY);

        assertThat(savedPost().getTargetCount()).isEqualTo(targetCount);
    }

    @Test
    @DisplayName("제목이 공백뿐이거나 51자면 400 INVALID_INPUT")
    void 제목이_비었거나_너무_길면_INVALID_INPUT() {
        assertThat(errorOf(contestRequest("   ", 4, TODAY, TODAY, null)))
                .isEqualTo(ErrorCode.INVALID_INPUT);
        assertThat(errorOf(contestRequest("가".repeat(51), 4, TODAY, TODAY, null)))
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("제목 글자 수는 이모지도 한 글자로 센다 (50자 통과)")
    void 이모지_50자_제목은_받는다() {
        givenContestDeadline(DEADLINE);
        givenSaveReturnsId(14L);
        String title = "😀".repeat(50);

        postCommandService.create(AUTHOR_ID, contestRequest(title, 4, TODAY, TODAY, null), TODAY);

        assertThat(savedPost().getTitle()).isEqualTo(title);
    }

    @Test
    @DisplayName("모집 내용이 2,001자면 400 INVALID_INPUT")
    void 내용이_너무_길면_INVALID_INPUT() {
        PostCreateRequest request =
                new PostCreateRequest(
                        CONTEST_ID, null, "제목", 4, TODAY, TODAY, null, "가".repeat(2001));

        assertThat(errorOf(request)).isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"javascript:alert(1)", "ftp://example.com", "example.com", "https://"})
    @DisplayName("관련 URL이 http(s)://로 시작하는 주소가 아니면 400 INVALID_INPUT")
    void 관련_URL_형식이_틀리면_INVALID_INPUT(String url) {
        assertThat(errorOf(contestRequest("제목", 4, TODAY, TODAY, url)))
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("외부 공모전 링크가 javascript: 주소면 400 INVALID_INPUT")
    void 외부_공모전_링크_형식이_틀리면_INVALID_INPUT() {
        given(contestQueryService.findCategoryId("IT_SW")).willReturn(Optional.of(1L));

        assertThat(errorOf(externalRequest(DEADLINE, "javascript:alert(1)")))
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("없는 분야 코드면 400 INVALID_INPUT")
    void 없는_분야_코드면_INVALID_INPUT() {
        given(contestQueryService.findCategoryId("IT_SW")).willReturn(Optional.empty());

        assertThat(errorOf(externalRequest(DEADLINE, null))).isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("관련 URL을 빈 칸으로 보내면 없는 값으로 저장한다")
    void 빈_관련_URL은_null로_저장된다() {
        givenContestDeadline(DEADLINE);
        givenSaveReturnsId(15L);

        postCommandService.create(AUTHOR_ID, contestRequest("제목", 4, TODAY, TODAY, " "), TODAY);

        assertThat(savedPost().getRelatedUrl()).isNull();
    }
}
