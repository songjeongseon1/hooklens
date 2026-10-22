package com.hooklens.global.exception;

import lombok.Getter;

/**
 * 서비스 로직에서 예상 가능한 실패를 표현하는 예외.
 * 예: throw new BusinessException(ErrorCode.ENDPOINT_NOT_FOUND);
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /**
     * 상황에 맞는 메시지를 직접 넣을 때 (오버로딩: 이름은 같고 파라미터만 다른 생성자).
     * 예: throw new BusinessException(ErrorCode.INVALID_INPUT, "헤더 값에 줄바꿈을 넣을 수 없습니다: X-Custom");
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);             // 부모(RuntimeException)에 메시지 전달 → getMessage()로 꺼낼 수 있다
        this.errorCode = errorCode; // 상태 코드·에러 코드는 그대로 ErrorCode에서
    }
}
