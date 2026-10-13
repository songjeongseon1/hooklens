package com.hooklens.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * API 명세서의 에러 코드표와 1:1로 맞춘 목록.
 * 새 에러가 생기면 명세서와 이 파일을 함께 고친다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    INVALID_INPUT(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    REPLAY_TARGET_BLOCKED(HttpStatus.BAD_REQUEST, "내부망 주소로는 재전송할 수 없습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN_ENDPOINT(HttpStatus.FORBIDDEN, "엔드포인트 소유자가 아닙니다."),
    ENDPOINT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 엔드포인트입니다."),
    REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 요청 로그입니다."),
    ENDPOINT_EXPIRED(HttpStatus.GONE, "만료된 엔드포인트입니다."),
    PAYLOAD_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "요청 바디는 1MB를 넘을 수 없습니다."),
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),
    REPLAY_FAILED(HttpStatus.BAD_GATEWAY, "재전송 대상 서버에 연결하지 못했습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;
}
