package com.hooklens.hook;

import com.hooklens.endpoint.Endpoint;
import com.hooklens.endpoint.EndpointService;
import com.hooklens.log.RequestLog;
import com.hooklens.log.RequestLogRepository;
import com.hooklens.log.RequestReceivedEvent;
import com.hooklens.log.dto.RequestLogSummary;
import com.hooklens.response.ResponseRule;
import com.hooklens.response.ResponseRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher eventPublisher; // Spring의 "방송 장치". 누가 듣는지는 모른다

    /**
     * 웹훅 요청을 저장하고, 돌려줄 기본 응답 규칙을 찾는다 (REQ-HOOK-001, 002).
     * 없는 slug는 404, 만료된 엔드포인트는 410 — getActiveBySlug가 예외를 던지므로 이때는 저장하지 않는다.
     * 저장 후 "새 요청" 이벤트를 발행한다 (REQ-LOG-001). 이 서비스는 SSE를 모르고 방송만 한다.
     */
    @Transactional
    public Optional<ResponseRule> receive(String slug, CapturedRequest captured) {
        Endpoint endpoint = endpointService.getActiveBySlug(slug);

        // 저장하고, DB가 매긴 id가 채워진 엔티티를 받는다 (요약 DTO를 만들려고)
        RequestLog saved = requestLogRepository.save(RequestLog.of(endpoint, captured, LocalDateTime.now(clock)));

        // "새 요청 저장됨" 방송. 아직 커밋 전이라 바로 전송되지 않는다 —
        // 리스너가 AFTER_COMMIT으로 듣기 때문에 Spring이 들고 있다가 커밋이 성공하면 그때 전달한다(실패하면 버린다).
        // 엔티티 → DTO 변환은 트랜잭션 안인 지금 한다 (리스너는 커밋 뒤라 엔티티의 LAZY 필드를 쓸 수 없다).
        eventPublisher.publishEvent(new RequestReceivedEvent(endpoint.getId(), RequestLogSummary.from(saved)));

        return responseRuleRepository.findByEndpointIdAndIsDefaultTrue(endpoint.getId());
    }
}
