package com.hooklens.log;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RequestLogRepository extends JpaRepository<RequestLog, Long> {

    /**
     * 커서 조회: endpoint_id = ? AND request_id < ? ORDER BY request_id DESC LIMIT ?
     * 인덱스 (endpoint_id, request_id DESC)와 순서가 같아 인덱스에서 바로 찾아 limit개만 읽는다.
     * Pageable(OFFSET)이 아니라 Limit을 쓴다 — OFFSET은 앞의 행을 세고 버려 뒤로 갈수록 느리다.
     */
    List<RequestLog> findByEndpointIdAndIdLessThanOrderByIdDesc(Long endpointId, Long cursor, Limit limit);

    /**
     * id와 엔드포인트가 둘 다 맞아야 찾는다: request_id = ? AND endpoint_id = ?
     * findById 후 자바로 확인하지 않고 조건을 쿼리에 넣어, 남의 로그는 애초에 조회되지 않게 한다(IDOR 방어).
     */
    Optional<RequestLog> findByIdAndEndpointId(Long id, Long endpointId);
}
