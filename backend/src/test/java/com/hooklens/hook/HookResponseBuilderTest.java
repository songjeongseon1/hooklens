package com.hooklens.hook;

import com.hooklens.response.ResponseRule;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 응답 규칙 → HTTP 응답 변환만 Spring 없이 빠르게 확인하는 단위 테스트.
 */
class HookResponseBuilderTest {

    private final HookResponseBuilder responseBuilder = new HookResponseBuilder(JsonMapper.builder().build());

    @Test
    void 전송_제어_헤더는_대소문자와_상관없이_무시한다() {
        ResponseRule rule = ResponseRule.createDefault(null, 200,
                "{\"CONTENT-LENGTH\": \"1\", \"connection\": \"close\", \"Keep-Alive\": \"timeout=5\", \"X-Keep\": \"yes\"}",
                "ok", 0);

        HttpHeaders headers = responseBuilder.build(rule).getHeaders();

        assertThat(headers.get("Content-Length")).isNull();
        assertThat(headers.get("Connection")).isNull();
        assertThat(headers.get("Keep-Alive")).isNull();
        assertThat(headers.getFirst("X-Keep")).isEqualTo("yes");
    }

    @Test
    void 헤더와_바디가_없는_규칙은_상태_코드만_응답한다() {
        ResponseRule rule = ResponseRule.createDefault(null, 204, null, null, 0);

        ResponseEntity<String> response = responseBuilder.build(rule);

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        assertThat(response.getHeaders().isEmpty()).isTrue();
        assertThat(response.getBody()).isNull();
    }

    @Test
    void 규칙이_없을_때의_기본_응답은_200_ok_true() {
        ResponseEntity<String> response = responseBuilder.defaultResponse();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo("{\"ok\":true}");
    }
}
