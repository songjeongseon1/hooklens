package com.hooklens.log;

import com.hooklens.endpoint.Endpoint;
import com.hooklens.endpoint.EndpointService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 새 요청 실시간 구독 (REQ-LOG-001).
 * 화면은 EventSource 대신 fetch 스트림으로 연결해 X-Anonymous-Token 헤더를 그대로 보낸다(CLAUDE.md 결정).
 *
 * <pre>
 * GET /api/endpoints/{slug}/requests        지금까지의 목록 (한 번에)
 * GET /api/endpoints/{slug}/requests/{id}   상세
 * GET /api/endpoints/{slug}/stream          앞으로 올 요청 (실시간)  ← 이 컨트롤러
 * </pre>
 */
@RestController                                 // 메서드 반환값이 곧 HTTP 응답
@RequestMapping("/api/endpoints/{slug}/stream")
@RequiredArgsConstructor                        // final 필드를 받는 생성자 자동 생성 → 생성자 주입
public class RequestStreamController {

    private final EndpointService endpointService; // 주인 확인용 (getOwnedBySlug)
    private final RequestStreamRegistry registry;  // 엔드포인트별 연결 목록

    /**
     * 연결을 열고 이 엔드포인트의 새 요청을 계속 받는다.
     * ApiResponse로 감싸지 않는다 — 끝나지 않는 스트림이라 감쌀 수 없다. Spring이 SseEmitter를 보면
     * Content-Type을 text/event-stream으로 정하고 응답을 열어 둔다.
     * 에러(401·403·404·410)는 SseEmitter를 만들기 전에 나므로 GlobalExceptionHandler가 평소처럼 JSON으로 응답한다.
     */
    @GetMapping
    public SseEmitter subscribe(
            @PathVariable String slug,
            @RequestHeader(value = "X-Anonymous-Token", required = false) String anonymousToken) { // 없으면 null → 401
        // 토큰 없음 401, 틀림 403, 없는 slug 404, 만료 410.
        // @Transactional 메서드라 끝나는 순간 DB 커넥션을 반납한다. open-in-view: false 덕분에
        // 연결이 30분 열려 있어도 커넥션을 붙잡지 않는다 (켜져 있으면 화면 10개만 열려도 커넥션 풀이 바닥난다).
        Endpoint endpoint = endpointService.getOwnedBySlug(slug, anonymousToken);
        return registry.register(endpoint.getId()); // 연결을 만들어 목록에 넣는다 → "connected" 메시지가 바로 나간다
    }
}
