package com.hooklens.hook;

import com.hooklens.response.ResponseRule;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 응답 규칙(ResponseRule)으로 웹훅 응답을 만든다 (REQ-HOOK-002).
 * 규칙이 없으면 기본 응답 200 {"ok":true}를 돌려준다.
 */
@Component
@RequiredArgsConstructor
public class HookResponseBuilder {

    /** 설정된 응답이 없을 때의 기본 응답 바디 */
    public static final String DEFAULT_RESPONSE_BODY = "{\"ok\":true}";

    /**
     * 서버(톰캣)가 직접 관리하는 전송 제어 헤더. 사용자가 설정해도 무시한다.
     * 예: Content-Length를 실제 바디 길이와 다르게 설정하면 클라이언트가 응답을 끝까지 못 읽거나 잘린다.
     * HTTP 헤더 이름은 대소문자를 구분하지 않으므로 소문자로 비교한다.
     */
    private static final Set<String> IGNORED_HEADERS = Set.of(
            "content-length", "transfer-encoding", "connection",
            "keep-alive", "upgrade", "te", "trailer");

    private final JsonMapper jsonMapper;

    /** 설정된 규칙대로 응답한다. 전송 제어 헤더는 빼고 넣는다. */
    public ResponseEntity<String> build(ResponseRule rule) {
        HttpHeaders headers = new HttpHeaders();
        parseHeaders(rule.getResponseHeaders()).forEach((name, value) -> {
            if (!IGNORED_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                headers.add(name, value);
            }
        });
        return ResponseEntity.status(rule.getStatusCode())
                .headers(headers)
                .body(rule.getResponseBody());
    }

    /** 규칙이 없을 때: 200 {"ok":true} */
    public ResponseEntity<String> defaultResponse() {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(DEFAULT_RESPONSE_BODY);
    }

    /** {"이름": "값"} 형식의 JSON을 Map으로 바꾼다. 비어 있으면 빈 Map. */
    private Map<String, String> parseHeaders(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        return jsonMapper.readValue(json, new TypeReference<Map<String, String>>() {});
    }
}
