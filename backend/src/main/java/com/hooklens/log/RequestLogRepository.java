package com.hooklens.log;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RequestLogRepository extends JpaRepository<RequestLog, Long> {

    /**
     * 커서 조회: endpoint_id = ? AND request_id < ? ORDER BY request_id DESC LIMIT ?
     * 인덱스 (endpoint_id, request_id DESC)와 순서가 같아 인덱스에서 바로 찾아 limit개만 읽는다.
     * Pageable(OFFSET)이 아니라 Limit을 쓴다 — OFFSET은 앞의 행을 세고 버려 뒤로 갈수록 느리다.
     */
    List<RequestLog> findByEndpointIdAndIdLessThanOrderByIdDesc(Long endpointId, Long cursor, Limit limit);
}
