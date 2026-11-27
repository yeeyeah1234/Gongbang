package com.gongbang.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DefaultTitleGeneratorTest {

    private static final String BOARD_BASE = "제목 없는 보드";
    private static final String DOC_BASE = "제목 없는 문서";

    @Test
    @DisplayName("해당하는 제목이 없으면 기준 이름을 돌려준다")
    void 없으면_기준_이름() {
        assertThat(DefaultTitleGenerator.nextTitle(BOARD_BASE, List.of())).isEqualTo(BOARD_BASE);
        assertThat(DefaultTitleGenerator.nextTitle(DOC_BASE, null)).isEqualTo(DOC_BASE);
    }

    @Test
    @DisplayName("기준 이름만 있으면 (2)를 붙인다")
    void 기준_이름만_있으면_2() {
        assertThat(DefaultTitleGenerator.nextTitle(BOARD_BASE, List.of(BOARD_BASE)))
                .isEqualTo("제목 없는 보드 (2)");
        assertThat(DefaultTitleGenerator.nextTitle(DOC_BASE, List.of(DOC_BASE)))
                .isEqualTo("제목 없는 문서 (2)");
    }

    @Test
    @DisplayName("(2)와 (5)가 있으면 가장 큰 번호 + 1인 (6)을 돌려준다")
    void 가장_큰_번호_다음() {
        assertThat(
                        DefaultTitleGenerator.nextTitle(
                                BOARD_BASE, List.of("제목 없는 보드 (2)", "제목 없는 보드 (5)")))
                .isEqualTo("제목 없는 보드 (6)");
    }

    @Test
    @DisplayName("사용자가 바꾼 제목이나 (복사본)·(abc)·(0)·음수·선행0 등은 무시한다")
    void 이상한_형태와_사용자_제목은_무시한다() {
        List<String> titles =
                Arrays.asList(
                        null,
                        "회의록",
                        "제목 없는 보드 (복사본)",
                        "제목 없는 보드 (abc)",
                        "제목 없는 보드 (0)",
                        "제목 없는 보드 (-1)",
                        "제목 없는 보드 (+2)",
                        "제목 없는 보드 (02)",
                        "제목 없는 보드 ( 3 )",
                        "제목 없는 보드 ()",
                        "제목 없는 보드 (99999999999999999999999)",
                        "제목 없는 보드(2)",
                        "제목 없는 보드 (2) 수정");

        assertThat(DefaultTitleGenerator.nextTitle(BOARD_BASE, titles)).isEqualTo(BOARD_BASE);
    }

    @Test
    @DisplayName("유효한 번호와 무시할 제목이 섞여 있으면 유효한 최댓값 + 1을 돌려준다")
    void 섞여_있으면_유효한_번호만_계산한다() {
        List<String> titles =
                List.of(
                        "회의록",
                        "제목 없는 문서 (복사본)",
                        "제목 없는 문서 (abc)",
                        "제목 없는 문서 (0)",
                        "제목 없는 문서",
                        "제목 없는 문서 (3)");

        assertThat(DefaultTitleGenerator.nextTitle(DOC_BASE, titles)).isEqualTo("제목 없는 문서 (4)");
    }
}
