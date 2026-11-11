package com.gongbang.global.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gongbang.global.error.ErrorResponse;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.ALWAYS)
public class ApiResponse<T> {

    private final boolean success;
    private final T data;
    private final ErrorResponse error;

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(true, null, null);
    }

    public static <T> ApiResponse<T> failure(ErrorResponse error) {
        return new ApiResponse<>(false, null, error);
    }

    public static <T> ApiResponse<T> failure(String code, String message) {
        return new ApiResponse<>(false, null, ErrorResponse.of(code, message));
    }
}
