package com.gongbang.global.error;

import com.gongbang.global.common.ApiResponse;
import com.gongbang.global.error.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("BusinessException: code={}, message={}", errorCode.getCode(), e.getMessage());
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(ApiResponse.failure(errorCode.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message =
                (fieldError != null)
                        ? fieldError.getDefaultMessage()
                        : ErrorCode.INVALID_INPUT.getMessage();
        log.warn("MethodArgumentNotValidException: {}", message);
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.getHttpStatus())
                .body(ApiResponse.failure(ErrorCode.INVALID_INPUT.getCode(), message));
    }

    /**
     * 요청 형식 오류: 깨진 JSON·본문 타입 불일치·본문 없음, 경로·쿼리 값 타입 불일치, 필수 쿼리 누락, 지원하지 않는
     * 메서드(405)·Content-Type(415). 모두 400 INVALID_INPUT으로 합친다 (11/17 팀 합의). 아래 Exception 처리기가 500으로
     * 바꾸지 않도록 따로 잡는다. 요청 본문에 이메일·비밀번호가 있을 수 있어 예외 이름만 남긴다 (AGENTS.md 9).
     */
    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class,
        HttpRequestMethodNotSupportedException.class,
        HttpMediaTypeNotSupportedException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception e) {
        log.warn("Bad request: {}", e.getClass().getSimpleName());
        return failure(ErrorCode.INVALID_INPUT);
    }

    /**
     * 없는 주소: 404 RESOURCE_NOT_FOUND. 대부분 봇 요청이라 로그를 남기지 않는다 (11/17 팀 합의). 지금 설정에서는 정적 리소스 처리기가
     * NoResourceFoundException을 던지고, 정적 리소스 처리를 끄면 NoHandlerFoundException이 온다.
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleNoResource(Exception e) {
        return failure(ErrorCode.RESOURCE_NOT_FOUND);
    }

    private static ResponseEntity<ApiResponse<Void>> failure(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(ApiResponse.failure(errorCode.getCode(), errorCode.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("Unhandled Exception: ", e);
        return ResponseEntity.status(ErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus())
                .body(
                        ApiResponse.failure(
                                ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                                ErrorCode.INTERNAL_SERVER_ERROR.getMessage()));
    }
}
