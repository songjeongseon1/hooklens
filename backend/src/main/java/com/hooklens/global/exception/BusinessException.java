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
}
