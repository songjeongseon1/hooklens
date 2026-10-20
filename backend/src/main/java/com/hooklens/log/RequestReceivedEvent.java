package com.hooklens.log;

import com.hooklens.log.dto.RequestLogSummary;

/**
 * "새 웹훅 요청이 저장됐다"는 방송 내용 (REQ-LOG-001).
 * 수신 쪽(HookReceiveService)은 이 이벤트만 발행하고, SSE가 있는지는 모른다.
 * 엔티티가 아니라 DTO를 담는다 — 리스너는 트랜잭션이 끝난 뒤 실행돼 엔티티의 LAZY 필드를 건드리면 오류가 난다.
 *
 * @param endpointId 어느 엔드포인트의 요청인지 → 그 엔드포인트를 보는 화면에만 보낸다
 * @param summary    화면에 보낼 내용. 목록 API의 한 줄과 같은 모양이라 화면이 그대로 목록 맨 위에 붙이면 된다
 */
public record RequestReceivedEvent(Long endpointId, RequestLogSummary summary) { // 값만 담아 전달하는 상자라 record
}
