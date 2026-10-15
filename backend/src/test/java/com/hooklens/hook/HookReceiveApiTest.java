package com.hooklens.hook;

import com.hooklens.TestcontainersConfiguration;
import com.hooklens.endpoint.Endpoint;
import com.hooklens.endpoint.EndpointRepository;
import com.hooklens.log.RequestLog;
import com.hooklens.log.RequestLogRepository;
import com.hooklens.response.ResponseRule;
import com.hooklens.response.ResponseRuleRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /h/{slug}/** 웹훅 수신 → request_log 저장까지 실제 MySQL로 확인하는 통합 테스트 (Docker 필요).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class HookReceiveApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EndpointRepository endpointRepository;

    @Autowired
    private RequestLogRepository requestLogRepository;

    @Autowired
    private Clock clock;

    @Autowired
    private ResponseRuleRepository responseRuleRepository;

    @Test
    void JSON_웹훅을_받으면_요청_정보를_그대로_저장하고_200_ok를_돌려준다() throws Exception {
        Endpoint endpoint = saveEndpoint(LocalDateTime.now(clock));
        String json = "{\"orderId\":\"ORD-1001\",\"status\":\"DONE\",\"memo\":\"결제 완료\"}";

        mockMvc.perform(post("/h/" + endpoint.getSlug() + "/payments/done?source=toss&retry=0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature", "abc123")
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"ok\":true}"));

        RequestLog log = singleLogOf(endpoint);
        assertThat(log.getMethod()).isEqualTo("POST");
        assertThat(log.getPath()).isEqualTo("/payments/done");
        assertThat(log.getQueryString()).isEqualTo("source=toss&retry=0");
        assertThat(log.getBody()).isEqualTo(json);
        assertThat(log.getSizeBytes()).isEqualTo(json.getBytes(StandardCharsets.UTF_8).length);
        assertThat(log.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(log.getIp()).isEqualTo("127.0.0.1");
        assertThat(log.getReceivedAt()).isNotNull();
        assertThat(JsonPath.<String>read(log.getHeaders(), "$['X-Signature'][0]")).isEqualTo("abc123");
    }

    @Test
    void 하위_경로와_바디가_없는_GET도_받는다() throws Exception {
        Endpoint endpoint = saveEndpoint(LocalDateTime.now(clock));

        mockMvc.perform(get("/h/" + endpoint.getSlug()))
                .andExpect(status().isOk());

        RequestLog log = singleLogOf(endpoint);
        assertThat(log.getMethod()).isEqualTo("GET");
        assertThat(log.getPath()).isEqualTo("/");
        assertThat(log.getQueryString()).isNull();
        assertThat(log.getBody()).isNull();
        assertThat(log.getSizeBytes()).isZero();
    }

    @Test
    void 폼_PUT_요청의_바디도_원본_그대로_저장한다() throws Exception {
        // FormContentFilter가 켜져 있으면 바디가 비어 저장된다 (application.yml 참고)
        Endpoint endpoint = saveEndpoint(LocalDateTime.now(clock));

        mockMvc.perform(put("/h/" + endpoint.getSlug())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("a=1&b=2"))
                .andExpect(status().isOk());

        assertThat(singleLogOf(endpoint).getBody()).isEqualTo("a=1&b=2");
    }

    @Test
    void 같은_이름의_헤더가_여러_번_오면_값을_모두_저장한다() throws Exception {
        Endpoint endpoint = saveEndpoint(LocalDateTime.now(clock));

        mockMvc.perform(post("/h/" + endpoint.getSlug()).header("X-Multi", "first", "second"))
                .andExpect(status().isOk());

        List<String> values = JsonPath.read(singleLogOf(endpoint).getHeaders(), "$['X-Multi']");
        assertThat(values).containsExactly("first", "second");
    }

    @Test
    void 없는_slug면_404_ENDPOINT_NOT_FOUND() throws Exception {
        mockMvc.perform(post("/h/notExist00"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ENDPOINT_NOT_FOUND"));
    }

    @Test
    void 만료된_엔드포인트면_410_ENDPOINT_EXPIRED이고_저장하지_않는다() throws Exception {
        // 25시간 전에 만든 비회원 엔드포인트 → 24시간 유효 기간이 지났다
        Endpoint endpoint = saveEndpoint(LocalDateTime.now(clock).minusHours(25));

        mockMvc.perform(post("/h/" + endpoint.getSlug()).content("late"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error.code").value("ENDPOINT_EXPIRED"));

        assertThat(logsOf(endpoint)).isEmpty();
    }

    @Test
    void 기본_응답_규칙이_있으면_설정한_상태_헤더_바디로_응답한다() throws Exception {
        // given: 201, XML 바디, 커스텀 헤더로 응답하라는 규칙
        Endpoint endpoint = saveEndpoint(LocalDateTime.now(clock));
        responseRuleRepository.save(ResponseRule.createDefault(endpoint, 201,
                "{\"Content-Type\": \"application/xml\", \"X-Custom\": \"hello\"}",
                "<result>accepted</result>", 0));

        // when & then: 웹훅을 보내면 규칙대로 응답한다
        mockMvc.perform(post("/h/" + endpoint.getSlug()))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Custom", "hello"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(content().string("<result>accepted</result>"));
    }

    @Test
    void 규칙에_전송_제어_헤더가_있어도_응답에는_넣지_않는다() throws Exception {
        // Content-Length를 실제 바디 길이와 다르게 보내면 클라이언트가 응답을 제대로 못 읽는다
        Endpoint endpoint = saveEndpoint(LocalDateTime.now(clock));
        responseRuleRepository.save(ResponseRule.createDefault(endpoint, 200,
                "{\"Content-Length\": \"999\", \"Transfer-Encoding\": \"chunked\", \"X-Keep\": \"yes\"}",
                "short", 0));

        mockMvc.perform(post("/h/" + endpoint.getSlug()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Length", not("999")))
                .andExpect(header().doesNotExist("Transfer-Encoding"))
                .andExpect(header().string("X-Keep", "yes"))
                .andExpect(content().string("short"));
    }

    @Test
    void 지연이_설정되면_그만큼_기다린_뒤_응답한다() throws Exception {
        Endpoint endpoint = saveEndpoint(LocalDateTime.now(clock));
        responseRuleRepository.save(ResponseRule.createDefault(endpoint, 200, null, null, 300));

        long start = System.nanoTime();
        mockMvc.perform(post("/h/" + endpoint.getSlug()))
                .andExpect(status().isOk());
        long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

        assertThat(elapsedMs).isGreaterThanOrEqualTo(300);
    }

    @Test
    void 지연_응답을_기다리는_동안에도_로그는_이미_저장돼_있다() throws Exception {
        // 대기를 트랜잭션 밖(컨트롤러)에서 하므로, 응답 전에 로그가 커밋돼 있어야 한다
        Endpoint endpoint = saveEndpoint(LocalDateTime.now(clock));
        responseRuleRepository.save(ResponseRule.createDefault(endpoint, 200, null, null, 1500));

        // 요청은 다른 스레드에서 보내 두고 (1.5초 뒤에 응답이 온다)
        CompletableFuture<Void> request = CompletableFuture.runAsync(() -> {
            try {
                mockMvc.perform(post("/h/" + endpoint.getSlug()).content("slow"))
                        .andExpect(status().isOk());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // 응답이 오기 전에 로그가 보이는지 최대 1초 동안 확인한다
        boolean savedBeforeResponse = waitUntil(() -> !logsOf(endpoint).isEmpty(), Duration.ofSeconds(1));

        assertThat(savedBeforeResponse).isTrue();
        assertThat(request).isNotDone();
        request.join();
    }

    private Endpoint saveEndpoint(LocalDateTime createdAt) {
        String slug = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        return endpointRepository.save(Endpoint.createAnonymous(slug, null, "0".repeat(64), createdAt));
    }

    private List<RequestLog> logsOf(Endpoint endpoint) {
        return requestLogRepository.findAll().stream()
                .filter(log -> log.getEndpoint().getId().equals(endpoint.getId()))
                .toList();
    }

    /** 조건이 참이 될 때까지 50ms 간격으로 확인한다. 시간 안에 참이 되면 true. */
    private boolean waitUntil(BooleanSupplier condition, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(50);
        }
        return false;
    }

    private RequestLog singleLogOf(Endpoint endpoint) {
        List<RequestLog> logs = logsOf(endpoint);
        assertThat(logs).hasSize(1);
        return logs.getFirst();
    }
}
