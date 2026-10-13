package com.hooklens.global.response;

import com.hooklens.global.exception.ErrorCode;

/**
 * 관리 API(/api/**) 공통 응답 형식.
 * { "success": true, "data": {...}, "error": null }
 * 웹훅 수신(/h/**)은 사용자가 설정한 응답을 그대로 돌려주므로 이 형식을 쓰지 않는다.
 */
public record ApiResponse<T>(boolean success, T data, ErrorBody error) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> fail(ErrorCode code) {
        return new ApiResponse<>(false, null, new ErrorBody(code.name(), code.getMessage()));
    }

    public static ApiResponse<Void> fail(ErrorCode code, String message) {
        return new ApiResponse<>(false, null, new ErrorBody(code.name(), message));
    }

    public record ErrorBody(String code, String message) {
    }
}
