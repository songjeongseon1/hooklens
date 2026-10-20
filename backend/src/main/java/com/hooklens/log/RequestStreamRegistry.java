package com.hooklens.log;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 엔드포인트별로 열려 있는 SSE 연결을 보관한다 (REQ-LOG-001).
 *
 * <pre>
 * emitters
 *  ├ 엔드포인트 7  → { 화면A의 연결, 화면B의 연결 }
 *  └ 엔드포인트 12 → { 화면C의 연결 }
 * </pre>
 *
 * 1차는 서버 1대라 메모리에 둔다. 서버가 여러 대가 되면 연결이 다른 서버에 붙어 있을 수 있어
 * Redis Pub/Sub로 바꿔야 한다(2차).
 */
@Component // Spring이 하나만 만들어 관리한다 → 웹훅 수신 쪽과 화면 연결 쪽이 같은 목록을 공유한다
public class RequestStreamRegistry {

    /** 연결 유지 시간(30분). 지나면 서버가 끊고, 화면이 다시 연결한다. */
    static final long TIMEOUT_MS = Duration.ofMinutes(30).toMillis();

    /**
     * 엔드포인트 id → 그 엔드포인트를 보고 있는 화면들의 연결.
     * 등록(화면 연결)·삭제(화면 닫힘)·전송(웹훅 수신)이 서로 다른 스레드에서 동시에 일어나므로
     * 일반 HashMap이 아니라 동시에 써도 안전한 ConcurrentHashMap을 쓴다.
     */
    private final Map<Long, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /**
     * 새 연결을 만들어 목록에 넣는다. 연결이 어떻게 끝나든(정상 종료·시간 초과·오류) 스스로 목록에서 빠지도록
     * 정리 작업(콜백)을 걸어 둔다 → 죽은 연결이 메모리에 쌓이지 않는다.
     */
    public SseEmitter register(Long endpointId) {
        // "끊지 않은 응답" 하나. 컨트롤러가 이걸 반환하면 Spring이 응답을 열어 두고, 나중에 send()로 데이터를 흘려보낸다
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);

        // compute: 이 엔드포인트 칸을 한 번에(원자적으로) 고친다.
        // get() 후 add()로 나눠 쓰면, 그 사이에 remove()가 빈 칸을 지워 새 연결이 사라질 수 있다.
        emitters.compute(endpointId, (id, set) -> {
            // 이 엔드포인트의 첫 연결이면 동시성에 안전한 새 Set을 만들고, 아니면 기존 Set을 쓴다
            Set<SseEmitter> connections = (set == null) ? ConcurrentHashMap.newKeySet() : set;
            connections.add(emitter); // 새 연결 추가
            return connections;       // 반환한 Set이 이 엔드포인트 칸에 저장된다
        });

        emitter.onCompletion(() -> remove(endpointId, emitter)); // 정상 종료(화면이 닫힘 등) → 목록에서 빼기
        emitter.onTimeout(() -> remove(endpointId, emitter));    // 30분 지남 → 목록에서 빼기
        emitter.onError(e -> remove(endpointId, emitter));       // 오류(네트워크 끊김 등) → 목록에서 빼기

        // 연결 직후 인사 메시지. 웹훅이 한동안 안 와도 화면이 "연결됨"을 바로 알 수 있다.
        // 아직 응답이 시작되기 전이지만, SseEmitter가 모아 뒀다가 응답이 시작되면 보낸다.
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok")); // event: connected / data: ok
        } catch (IOException e) {
            remove(endpointId, emitter); // 실패하면 바로 정리
        }
        return emitter;
    }

    /**
     * 이 엔드포인트를 보고 있는 모든 화면에 이벤트를 보낸다.
     * 보내다 실패한 연결은 이미 떠난 화면이므로 목록에서 정리한다.
     */
    public void send(Long endpointId, String eventName, Object data) {
        Set<SseEmitter> connections = emitters.get(endpointId); // 이 엔드포인트의 연결들
        if (connections == null) {
            return; // 아무도 화면을 열어 두지 않았으면 보낼 곳이 없다 (대부분의 웹훅이 이 경우)
        }
        for (SseEmitter emitter : connections) {
            // try를 for 안에 둔다 → 화면 하나가 실패해도 나머지 화면에는 계속 보낸다
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)  // "event: request" 줄 → 화면이 메시지 종류를 구분
                        .data(data));     // "data: {...}" 줄 → 객체는 JSON으로 바뀌어 나간다
            } catch (IOException | IllegalStateException e) {
                // 화면이 탭을 닫았거나 이미 끝난 연결 → 목록에서 빼서 다음부터 시도하지 않는다.
                // newKeySet()으로 만든 Set이라 돌면서 지워도 ConcurrentModificationException이 나지 않는다.
                remove(endpointId, emitter);
            }
        }
    }

    /** 하트비트 주기(30초). 프록시(Nginx 기본 60초)가 조용한 연결을 끊기 전에 보낸다. */
    static final long HEARTBEAT_INTERVAL_MS = 30_000; // _는 읽기 쉽게 하려는 구분자 (= 30000)

    /**
     * 열려 있는 모든 연결에 SSE 주석(": heartbeat")을 보낸다. 화면은 주석을 무시하지만,
     * 데이터가 흘렀으니 프록시가 연결을 끊지 않는다. 실패한 연결은 이미 떠난 화면이라 정리된다.
     */
    @Scheduled(fixedRate = HEARTBEAT_INTERVAL_MS) // 30초마다 자동 실행 (SchedulingConfig의 @EnableScheduling 필요)
    public void sendHeartbeat() {
        // 모든 엔드포인트 칸을 돈다. ConcurrentHashMap이라 도는 중에 다른 스레드가 고쳐도 안전하다
        emitters.forEach((endpointId, connections) -> {
            for (SseEmitter emitter : connections) {
                try {
                    emitter.send(SseEmitter.event().comment("heartbeat")); // data:가 아니라 ": heartbeat" 주석 줄
                } catch (IOException | IllegalStateException e) {
                    remove(endpointId, emitter); // 실패 = 이미 떠난 화면 → 목록에서 정리
                }
            }
        });
    }

    /** 연결 하나를 목록에서 뺀다. 마지막 연결이었으면 엔드포인트 칸 자체를 지운다. */
    private void remove(Long endpointId, SseEmitter emitter) {
        // computeIfPresent: 칸이 있을 때만 한 번에 고친다 (없으면 아무것도 안 함)
        emitters.computeIfPresent(endpointId, (id, set) -> {
            set.remove(emitter);
            // compute 계열에서 null을 반환하면 그 칸이 Map에서 삭제된다 → 아무도 안 보는 엔드포인트의 빈 Set이 남지 않는다
            return set.isEmpty() ? null : set;
        });
    }

    /** 테스트용: 이 엔드포인트에 열려 있는 연결 수. public이 아니라 같은 패키지(테스트 포함)에서만 쓴다. */
    int connectionCount(Long endpointId) {
        Set<SseEmitter> set = emitters.get(endpointId);
        return (set == null) ? 0 : set.size(); // 칸이 없으면 연결 0개
    }
}
