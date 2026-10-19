package com.hooklens.log.dto;

import com.hooklens.global.config.TimeConfig;
import com.hooklens.log.RequestLog;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 요청 상세 (REQ-LOG-003). 목록(RequestLogSummary)에서 뺐던 헤더·바디까지 모두 담는다.
 *
 * @param headers     {"이름": ["값", ...]} — DB의 JSON 문자열을 Map으로 바꿔 JSON 객체로 내보낸다
 * @param queryString 원본 쿼리 문자열. 화면에서 URLSearchParams로 나눠 보여 준다
 */
public record RequestLogDetail(
        Long requestId,
        String method,
        String path,
        String queryString,
        Map<String, List<String>> headers,
        String body,
        String contentType,
        String ip,
        int sizeBytes,
        OffsetDateTime receivedAt
) {

    /** 헤더 변환에는 JsonMapper(Spring 빈)가 필요해서, 변환은 서비스가 하고 결과만 받는다. */
    public static RequestLogDetail of(RequestLog log, Map<String, List<String>> headers) {
        return new RequestLogDetail(
                log.getId(),
                log.getMethod(),
                log.getPath(),
                log.getQueryString(),
                headers,
                log.getBody(),
                log.getContentType(),
                log.getIp(),
                log.getSizeBytes(),
                log.getReceivedAt().atZone(TimeConfig.ZONE).toOffsetDateTime()
        );
    }
}
