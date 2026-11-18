package com.gongbang.global.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gongbang.global.common.ApiResponse;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** 요청 형식 오류가 500이 아니라 400·404로 나가는지 (11/17 팀 합의). 재현했던 경우를 하나씩 보낸다. */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(new TestController())
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
    }

    record Body(Integer count, LocalDate date) {}

    @RestController
    static class TestController {

        @PostMapping("/test/body")
        ApiResponse<Void> body(@RequestBody Body body) {
            return ApiResponse.success();
        }

        @GetMapping("/test/items/{id}")
        ApiResponse<Void> item(@PathVariable Long id) {
            return ApiResponse.success();
        }

        @GetMapping("/test/page")
        ApiResponse<Void> page(@RequestParam(defaultValue = "0") int page) {
            return ApiResponse.success();
        }

        @GetMapping("/test/required")
        ApiResponse<Void> required(@RequestParam String nickname) {
            return ApiResponse.success();
        }

        @GetMapping("/test/no-resource")
        ApiResponse<Void> noResource() throws NoResourceFoundException {
            // 실제 서버에서 정적 리소스 처리기가 없는 주소에 던지는 예외
            throw new NoResourceFoundException(
                    org.springframework.http.HttpMethod.GET, "wp-login.php");
        }

        @GetMapping("/test/boom")
        ApiResponse<Void> boom() {
            throw new IllegalStateException("진짜 서버 오류");
        }
    }

    private static void expectInvalidInput(ResultActions result) throws Exception {
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.error.message").value(ErrorCode.INVALID_INPUT.getMessage()));
    }

    private ResultActions postBody(String json) throws Exception {
        return mockMvc.perform(
                post("/test/body").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    @DisplayName("깨진 JSON 본문은 400 INVALID_INPUT")
    void 깨진_JSON() throws Exception {
        expectInvalidInput(postBody("{ \"count\": "));
    }

    @Test
    @DisplayName("본문 값 타입이 틀리면 400 INVALID_INPUT (숫자 자리에 글자)")
    void 본문_타입_불일치() throws Exception {
        expectInvalidInput(postBody("{ \"count\": \"네명\" }"));
    }

    @Test
    @DisplayName("날짜 형식이 틀리면 400 INVALID_INPUT (2026/11/17)")
    void 날짜_형식_불일치() throws Exception {
        expectInvalidInput(postBody("{ \"date\": \"2026/11/17\" }"));
    }

    @Test
    @DisplayName("본문이 없으면 400 INVALID_INPUT")
    void 본문_없음() throws Exception {
        expectInvalidInput(
                mockMvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON)));
    }

    @Test
    @DisplayName("지원하지 않는 Content-Type(415)도 400 INVALID_INPUT")
    void 지원하지_않는_Content_Type() throws Exception {
        expectInvalidInput(
                mockMvc.perform(
                        post("/test/body").contentType(MediaType.TEXT_PLAIN).content("hello")));
    }

    @Test
    @DisplayName("경로 값 타입이 틀리면 400 INVALID_INPUT (/items/abc)")
    void 경로_타입_불일치() throws Exception {
        expectInvalidInput(mockMvc.perform(get("/test/items/abc")));
    }

    @Test
    @DisplayName("쿼리 값 타입이 틀리면 400 INVALID_INPUT (?page=abc)")
    void 쿼리_타입_불일치() throws Exception {
        expectInvalidInput(mockMvc.perform(get("/test/page").param("page", "abc")));
    }

    @Test
    @DisplayName("필수 쿼리가 없으면 400 INVALID_INPUT")
    void 필수_쿼리_누락() throws Exception {
        expectInvalidInput(mockMvc.perform(get("/test/required")));
    }

    @Test
    @DisplayName("지원하지 않는 메서드(405)도 400 INVALID_INPUT")
    void 지원하지_않는_메서드() throws Exception {
        expectInvalidInput(mockMvc.perform(put("/test/items/1")));
    }

    @Test
    @DisplayName("없는 주소(정적 리소스 처리기)는 404 RESOURCE_NOT_FOUND")
    void 없는_리소스() throws Exception {
        mockMvc.perform(get("/test/no-resource"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("매핑이 없는 주소(NoHandlerFoundException)도 404 RESOURCE_NOT_FOUND")
    void 매핑_없는_주소() throws Exception {
        mockMvc.perform(get("/test/nothing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("그 밖의 예외는 그대로 500 INTERNAL_SERVER_ERROR")
    void 진짜_서버_오류는_500() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_SERVER_ERROR"));
    }
}
