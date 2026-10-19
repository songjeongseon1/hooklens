package com.hooklens.log;

import com.hooklens.endpoint.Endpoint;
import com.hooklens.endpoint.EndpointService;
import com.hooklens.log.dto.RequestLogPage;
import com.hooklens.log.dto.RequestLogSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RequestLogService {

    private final EndpointService endpointService;
    private final RequestLogRepository requestLogRepository;

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
}
