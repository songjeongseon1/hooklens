package com.hooklens.hook;

import com.hooklens.endpoint.Endpoint;
import com.hooklens.endpoint.EndpointService;
import com.hooklens.log.RequestLog;
import com.hooklens.log.RequestLogRepository;
import com.hooklens.response.ResponseRule;
import com.hooklens.response.ResponseRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class HookReceiveService {

    private final EndpointService endpointService;
    private final RequestLogRepository requestLogRepository;
    private final ResponseRuleRepository responseRuleRepository;
    private final Clock clock;

    /**
     * 웹훅 요청을 저장하고, 돌려줄 기본 응답 규칙을 찾는다 (REQ-HOOK-001, 002).
     * 없는 slug는 404, 만료된 엔드포인트는 410 — getActiveBySlug가 예외를 던지므로 이때는 저장하지 않는다.
     */
    @Transactional
    public Optional<ResponseRule> receive(String slug, CapturedRequest captured) {
        Endpoint endpoint = endpointService.getActiveBySlug(slug);
        requestLogRepository.save(RequestLog.of(endpoint, captured, LocalDateTime.now(clock)));
        return responseRuleRepository.findByEndpointIdAndIsDefaultTrue(endpoint.getId());
    }
}
