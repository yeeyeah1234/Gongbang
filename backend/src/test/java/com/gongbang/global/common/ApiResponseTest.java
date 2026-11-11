package com.gongbang.global.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.gongbang.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApiResponseTest {

    @Test
    @DisplayName("성공 응답 생성 시 success=true, data 포함, error=null")
    void successResponse() {
        ApiResponse<String> response = ApiResponse.success("test data");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEqualTo("test data");
        assertThat(response.getError()).isNull();
    }

    @Test
    @DisplayName("실패 응답 생성 시 success=false, data=null, error 포함")
    void failureResponse() {
        ApiResponse<Void> response = ApiResponse.failure(ErrorCode.POST_FULL.getCode(), ErrorCode.POST_FULL.getMessage());

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getData()).isNull();
        assertThat(response.getError()).isNotNull();
        assertThat(response.getError().getCode()).isEqualTo("POST_FULL");
        assertThat(response.getError().getMessage()).isEqualTo("모집 인원이 모두 충원되었습니다.");
    }
}
