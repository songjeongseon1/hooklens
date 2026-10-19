package com.hooklens.log.dto;

import com.hooklens.global.config.TimeConfig;
import com.hooklens.log.RequestLog;

import java.time.OffsetDateTime;

/**
 * 요청 목록의 한 줄. 헤더·바디는 무거워서 빼고, 상세 조회(REQ-LOG-003)에서 보여 준다.
 * 엔티티를 그대로 내보내지 않는 이유: 무거운 필드 제외, LAZY 필드(endpoint) 직렬화 오류 방지, API 모양 고정.
 */
public record RequestLogSummary(
        Long requestId,
        String method,
        String path,
        String queryString,
        String contentType,
        String ip,
        int sizeBytes,
        OffsetDateTime receivedAt
) {

    public static RequestLogSummary from(RequestLog log) {
        return new RequestLogSummary(
                log.getId(),
                log.getMethod(),
                log.getPath(),
                log.getQueryString(),
                log.getContentType(),
                log.getIp(),
                log.getSizeBytes(),
                log.getReceivedAt().atZone(TimeConfig.ZONE).toOffsetDateTime()
        );
    }
}
