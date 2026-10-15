package com.hooklens.hook;

import com.hooklens.response.ResponseRule;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * 외부 서비스가 보내는 웹훅을 받는 입구.
 * 관리 API(/api/**)와 달리 인증이 없고, 어떤 HTTP 메서드·하위 경로든 받는다.
 */
@RestController
@RequiredArgsConstructor
public class HookReceiveController {

    private final HookRequestReader requestReader;
    private final HookReceiveService hookReceiveService;
    private final HookResponseBuilder responseBuilder;

    /** method를 지정하지 않으면 모든 메서드를 받는다. /** 는 하위 경로가 없는 /h/{slug}도 포함한다. */
    @RequestMapping("/h/{slug}/**")
    public ResponseEntity<String> receive(@PathVariable String slug, HttpServletRequest request) {
        Optional<ResponseRule> rule = hookReceiveService.receive(slug, requestReader.read(request, slug));
        rule.ifPresent(r -> delay(r.getDelayMs()));
        return rule.map(responseBuilder::build)
                .orElseGet(responseBuilder::defaultResponse);
    }

    /**
     * 설정된 시간만큼 기다린다. 가상 스레드에서 실행되므로 기다리는 동안 OS 스레드를 점유하지 않는다.
     * 서비스의 트랜잭션이 끝난 뒤라 로그는 이미 저장됐고 DB 커넥션도 반납된 상태다.
     */
    private void delay(int delayMs) {
        if (delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // 중단 신호를 삼키지 않고 위쪽(톰캣)에 전달
        }
    }
}
