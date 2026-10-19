package com.hooklens.log;

import com.hooklens.TestcontainersConfiguration;
import com.hooklens.endpoint.AnonymousTokenProvider;
import com.hooklens.endpoint.Endpoint;
import com.hooklens.endpoint.EndpointRepository;
import com.hooklens.hook.CapturedRequest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/endpoints/{slug}/requests 요청 목록 커서 조회 통합 테스트 (Docker 필요).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class RequestLogApiTest {

    private static final String TOKEN = "test-anonymous-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EndpointRepository endpointRepository;

    @Autowired
    private RequestLogRepository requestLogRepository;

    @Autowired
    private AnonymousTokenProvider tokenProvider;

    @Autowired
    private Clock clock;

    @Test
    void 최신순으로_size개를_주고_다음_커서를_준다() throws Exception {
        // given: 로그 3개가 있는 엔드포인트 (ids = [가장 오래된, 중간, 최신])
        Endpoint endpoint = saveEndpoint();
        List<Long> ids = saveLogs(endpoint, 3);

        // when: 2개만 달라고 요청
        String body = mockMvc.perform(get("/api/endpoints/" + endpoint.getSlug() + "/requests")
                        .header("X-Anonymous-Token", TOKEN)
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // then: 최신 2개가 최신순으로 오고, 다음 커서는 이번 페이지 마지막(중간)의 id
        assertThat(requestIdsOf(body)).containsExactly(ids.get(2), ids.get(1));
        assertThat(JsonPath.<Number>read(body, "$.data.nextCursor").longValue()).isEqualTo(ids.get(1));
    }

    @Test
    void 토큰이_없으면_401_UNAUTHORIZED() throws Exception {
        Endpoint endpoint = saveEndpoint();

        mockMvc.perform(get("/api/endpoints/" + endpoint.getSlug() + "/requests"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void nextCursor로_요청하면_다음_페이지를_주고_마지막이면_커서가_null() throws Exception {
        // given: 로그 3개 (첫 페이지 size=2에서 nextCursor로 중간 로그의 id를 받았다고 가정)
        Endpoint endpoint = saveEndpoint();
        List<Long> ids = saveLogs(endpoint, 3);

        // when: 그 커서로 다음 페이지 요청
        String body = mockMvc.perform(get("/api/endpoints/" + endpoint.getSlug() + "/requests")
                        .header("X-Anonymous-Token", TOKEN)
                        .param("size", "2")
                        .param("cursor", String.valueOf(ids.get(1))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // then: 남은 가장 오래된 1개만 오고, 더 없으므로 nextCursor는 null
        assertThat(requestIdsOf(body)).containsExactly(ids.get(0));
        assertThat(JsonPath.<Object>read(body, "$.data.nextCursor")).isNull();
    }

    @Test
    void 다른_엔드포인트의_로그는_섞이지_않는다() throws Exception {
        Endpoint mine = saveEndpoint();
        Endpoint other = saveEndpoint();
        List<Long> myIds = saveLogs(mine, 1);
        saveLogs(other, 2);

        String body = mockMvc.perform(get("/api/endpoints/" + mine.getSlug() + "/requests")
                        .header("X-Anonymous-Token", TOKEN))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(requestIdsOf(body)).containsExactly(myIds.get(0));
    }

    @Test
    void 토큰이_틀리면_403_FORBIDDEN_ENDPOINT() throws Exception {
        Endpoint endpoint = saveEndpoint();

        mockMvc.perform(get("/api/endpoints/" + endpoint.getSlug() + "/requests")
                        .header("X-Anonymous-Token", "someone-elses-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN_ENDPOINT"));
    }

    @ParameterizedTest(name = "{0}={1} → 400")
    @CsvSource({"size, 0", "size, 101", "cursor, abc"})
    void 잘못된_파라미터면_400_INVALID_INPUT(String name, String value) throws Exception {
        Endpoint endpoint = saveEndpoint();

        mockMvc.perform(get("/api/endpoints/" + endpoint.getSlug() + "/requests")
                        .header("X-Anonymous-Token", TOKEN)
                        .param(name, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }
    @Test
    void 남의_엔드포인트_요청_id로는_상세를_볼_수_없다() throws Exception {
        // 내 slug + 내 토큰이라 주인 확인은 통과하지만, 요청 id가 남의 것이면 404 (IDOR 방어)
        Endpoint mine = saveEndpoint();
        Endpoint other = saveEndpoint();
        Long otherRequestId = saveLogs(other, 1).getFirst();

        mockMvc.perform(get("/api/endpoints/" + mine.getSlug() + "/requests/" + otherRequestId)
                        .header("X-Anonymous-Token", TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("REQUEST_NOT_FOUND"));
    }

    @Test
    void 상세는_헤더를_객체로_주고_쿼리와_바디까지_모두_보여준다() throws Exception {
        // given: 헤더(같은 이름 두 번)·쿼리·바디가 있는 요청 하나
        Endpoint endpoint = saveEndpoint();
        String body = "{\"orderId\":\"ORD-1001\"}";
        CapturedRequest captured = new CapturedRequest(
                "POST", "/payments", "source=toss&retry=0",
                "{\"Content-Type\":[\"application/json\"],\"X-Multi\":[\"first\",\"second\"]}",
                body, "application/json", "203.0.113.7", body.length());
        Long requestId = requestLogRepository.save(
                RequestLog.of(endpoint, captured, LocalDateTime.now(clock))).getId();

        // when & then: 모든 값이 그대로 나오고, 헤더는 문자열이 아니라 객체
        mockMvc.perform(get("/api/endpoints/" + endpoint.getSlug() + "/requests/" + requestId)
                        .header("X-Anonymous-Token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestId").value(requestId))
                .andExpect(jsonPath("$.data.method").value("POST"))
                .andExpect(jsonPath("$.data.path").value("/payments"))
                .andExpect(jsonPath("$.data.queryString").value("source=toss&retry=0"))
                .andExpect(jsonPath("$.data.headers['Content-Type'][0]").value("application/json"))
                .andExpect(jsonPath("$.data.headers['X-Multi'][0]").value("first"))
                .andExpect(jsonPath("$.data.headers['X-Multi'][1]").value("second"))
                .andExpect(jsonPath("$.data.body").value(body))
                .andExpect(jsonPath("$.data.contentType").value("application/json"))
                .andExpect(jsonPath("$.data.ip").value("203.0.113.7"))
                .andExpect(jsonPath("$.data.sizeBytes").value(body.length()))
                .andExpect(jsonPath("$.data.receivedAt").value(endsWith("+09:00")));
    }

    @Test
    void 없는_요청_id면_404_REQUEST_NOT_FOUND() throws Exception {
        Endpoint endpoint = saveEndpoint();

        mockMvc.perform(get("/api/endpoints/" + endpoint.getSlug() + "/requests/" + Long.MAX_VALUE)
                        .header("X-Anonymous-Token", TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("REQUEST_NOT_FOUND"));
    }

    @Test
    void 상세도_토큰이_틀리면_403_FORBIDDEN_ENDPOINT() throws Exception {
        Endpoint endpoint = saveEndpoint();
        Long requestId = saveLogs(endpoint, 1).getFirst();

        mockMvc.perform(get("/api/endpoints/" + endpoint.getSlug() + "/requests/" + requestId)
                        .header("X-Anonymous-Token", "someone-elses-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN_ENDPOINT"));
    }

    /** TOKEN으로 관리할 수 있는 비회원 엔드포인트 */
    private Endpoint saveEndpoint() {
        String slug = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        return endpointRepository.save(
                Endpoint.createAnonymous(slug, null, tokenProvider.hash(TOKEN), LocalDateTime.now(clock)));
    }

    /** 로그를 count개 저장하고 id를 저장한 순서(오래된 순)로 돌려준다 */
    private List<Long> saveLogs(Endpoint endpoint, int count) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            CapturedRequest captured = new CapturedRequest(
                    "POST", "/n" + i, null, "{}", null, null, "127.0.0.1", 0);
            ids.add(requestLogRepository.save(
                    RequestLog.of(endpoint, captured, LocalDateTime.now(clock))).getId());
        }
        return ids;
    }

    /** 응답 JSON에서 requestId 목록만 꺼낸다 (작은 숫자는 Integer로 읽히므로 Long으로 맞춘다) */
    private List<Long> requestIdsOf(String body) {
        List<Number> ids = JsonPath.read(body, "$.data.items[*].requestId");
        return ids.stream().map(Number::longValue).toList();
    }
}
