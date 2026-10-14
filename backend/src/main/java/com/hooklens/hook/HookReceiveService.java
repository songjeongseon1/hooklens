package com.hooklens.hook;

import com.hooklens.endpoint.Endpoint;
import com.hooklens.endpoint.EndpointService;
import com.hooklens.log.RequestLog;
import com.hooklens.log.RequestLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class HookReceiveService {

    private final EndpointService endpointService;
    private final RequestLogRepository requestLogRepository;
    private final Clock clock;

    /**
     * 웹훅 요청을 저장한다 (REQ-HOOK-001).
     * 없는 slug는 404, 만료된 엔드포인트는 410 — getActiveBySlug가 예외를 던지므로 이때는 저장하지 않는다.
     */
    @Transactional
    public RequestLog receive(String slug, CapturedRequest captured) {
        Endpoint endpoint = endpointService.getActiveBySlug(slug);
        return requestLogRepository.save(RequestLog.of(endpoint, captured, LocalDateTime.now(clock)));
    }
}
