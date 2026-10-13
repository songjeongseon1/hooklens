package com.hooklens.endpoint.dto;

import jakarta.validation.constraints.Size;

/**
 * @param name 엔드포인트 이름(선택). 바디 없이 요청해도 된다.
 */
public record CreateEndpointRequest(
        @Size(max = 50, message = "이름은 50자 이하로 입력해 주세요.")
        String name
) {
}
