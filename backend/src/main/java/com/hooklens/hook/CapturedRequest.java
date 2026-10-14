package com.hooklens.hook;

/**
 * HTTP 요청에서 꺼낸 값만 담은 객체.
 * 서비스와 엔티티가 HttpServletRequest에 직접 의존하지 않게 해서 테스트와 재사용을 쉽게 한다.
 */
public record CapturedRequest(
        String method,
        String path,
        String queryString,
        String headersJson,
        String body,
        String contentType,
        String ip,
        int sizeBytes
) {
}
