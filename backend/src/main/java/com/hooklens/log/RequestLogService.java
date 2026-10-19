package com.hooklens.log;

import com.hooklens.endpoint.Endpoint;
import com.hooklens.endpoint.EndpointService;
import com.hooklens.global.exception.BusinessException;
import com.hooklens.global.exception.ErrorCode;
import com.hooklens.log.dto.RequestLogDetail;
import com.hooklens.log.dto.RequestLogPage;
import com.hooklens.log.dto.RequestLogSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RequestLogService {

    private final EndpointService endpointService;
    private final RequestLogRepository requestLogRepository;
    private final JsonMapper jsonMapper;

    /**
     * 요청 목록을 최신순으로 size개 조회한다 (REQ-LOG-002).
     * cursor가 없으면 첫 페이지, 있으면 그 id보다 오래된 것부터.
     * 오프셋이 아닌 커서 방식이라, 보는 중에 새 웹훅이 들어와도 페이지가 밀려 중복·누락되지 않는다.
     */
    @Transactional(readOnly = true)
    public RequestLogPage getPage(String slug, String anonymousToken, Long cursor, int size) {
        Endpoint endpoint = endpointService.getOwnedBySlug(slug, anonymousToken);
        long from = (cursor == null) ? Long.MAX_VALUE : cursor;

        // 다음 페이지가 있는지 알기 위해 하나 더 조회한다 (COUNT 쿼리 없이)
        List<RequestLog> logs = requestLogRepository.findByEndpointIdAndIdLessThanOrderByIdDesc(
                endpoint.getId(), from, Limit.of(size + 1));

        boolean hasNext = logs.size() > size;
        List<RequestLog> pageLogs = hasNext ? logs.subList(0, size) : logs;
        Long nextCursor = hasNext ? pageLogs.getLast().getId() : null;

        return new RequestLogPage(
                pageLogs.stream().map(RequestLogSummary::from).toList(),
                nextCursor);
    }

    /**
     * 요청 하나를 헤더·바디까지 조회한다 (REQ-LOG-003).
     * 주인 확인(getOwnedBySlug)은 "slug가 내 것인가"만 본다. "요청 id가 그 slug의 것인가"는
     * findByIdAndEndpointId가 따로 막는다 — 남의 로그면 존재 여부를 숨기려고 403이 아닌 404.
     */
    @Transactional(readOnly = true)
    public RequestLogDetail getDetail(String slug, String anonymousToken, Long requestId) {
        Endpoint endpoint = endpointService.getOwnedBySlug(slug, anonymousToken);
        RequestLog log = requestLogRepository.findByIdAndEndpointId(requestId, endpoint.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.REQUEST_NOT_FOUND));
        Map<String, List<String>> headers = jsonMapper.readValue(
                log.getHeaders(), new TypeReference<Map<String, List<String>>>() {});
        return RequestLogDetail.of(log, headers);
    }
}
