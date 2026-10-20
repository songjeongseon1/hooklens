package com.hooklens.log;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * "새 요청 저장됨" 이벤트를 듣고, 그 엔드포인트를 보고 있는 화면들에 SSE로 보낸다 (REQ-LOG-001).
 * 주의: @TransactionalEventListener는 트랜잭션 안에서 발행된 이벤트만 받는다(밖에서 발행하면 조용히 무시된다).
 * HookReceiveService.receive가 @Transactional이라 괜찮다.
 */
@Component // Spring이 관리하는 객체로 등록 → 이벤트를 받을 수 있다
@RequiredArgsConstructor
public class RequestReceivedListener {

    /** 화면에 나가는 "event: request" 줄의 이름. 화면은 이 이름으로 새 요청 메시지를 구분한다. */
    static final String EVENT_NAME = "request";

    private final RequestStreamRegistry registry; // 엔드포인트별 연결 목록

    /**
     * 커밋이 성공한 뒤에만 실행된다(AFTER_COMMIT).
     * - 커밋 전에 보내면: 화면이 알림을 받고 상세 API를 불렀을 때 아직 커밋 전이라 404가 날 수 있다.
     * - 저장이 실패(롤백)하면: 이 메서드는 아예 불리지 않는다 → 없는 요청을 알리는 "유령 알림"이 없다.
     * 일반 @EventListener는 publishEvent 즉시(커밋 전) 실행돼 위 두 문제가 생긴다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRequestReceived(RequestReceivedEvent event) { // 파라미터 타입으로 어떤 이벤트를 들을지 정해진다
        // 그 엔드포인트의 화면들에 "event: request" + 요약 JSON. 아무도 안 보고 있으면 그냥 끝난다
        registry.send(event.endpointId(), EVENT_NAME, event.summary());
    }
}
