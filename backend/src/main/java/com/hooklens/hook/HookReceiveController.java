package com.hooklens.hook;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 외부 서비스가 보내는 웹훅을 받는 입구.
 * 관리 API(/api/**)와 달리 인증이 없고, 어떤 HTTP 메서드·하위 경로든 받는다.
 */
@RestController
@RequiredArgsConstructor
public class HookReceiveController {

    /** 설정된 응답이 없을 때의 기본 응답. 응답 설정(REQ-HOOK-002)에서 이 부분이 바뀐다. */
    static final String DEFAULT_RESPONSE_BODY = "{\"ok\":true}";

    private final HookRequestReader requestReader;
    private final HookReceiveService hookReceiveService;

    /** method를 지정하지 않으면 모든 메서드를 받는다. /** 는 하위 경로가 없는 /h/{slug}도 포함한다. */
    @RequestMapping("/h/{slug}/**")
    public ResponseEntity<String> receive(@PathVariable String slug, HttpServletRequest request) {
        hookReceiveService.receive(slug, requestReader.read(request, slug));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(DEFAULT_RESPONSE_BODY);
    }
}
