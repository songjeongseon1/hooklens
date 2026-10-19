package com.hooklens.log.dto;

import java.util.List;

/**
 * 요청 목록 한 페이지.
 *
 * @param nextCursor 다음 페이지 요청에 그대로 넣을 값. 마지막 페이지면 null
 */
public record RequestLogPage(List<RequestLogSummary> items, Long nextCursor) {
}
