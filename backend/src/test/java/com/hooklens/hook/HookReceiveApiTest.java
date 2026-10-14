package com.hooklens.hook;

import com.hooklens.TestcontainersConfiguration;
import com.hooklens.endpoint.Endpoint;
import com.hooklens.endpoint.EndpointRepository;
import com.hooklens.log.RequestLog;
import com.hooklens.log.RequestLogRepository;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

    private Endpoint saveEndpoint(LocalDateTime createdAt) {
        String slug = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        return endpointRepository.save(Endpoint.createAnonymous(slug, null, "0".repeat(64), createdAt));
    }

    private List<RequestLog> logsOf(Endpoint endpoint) {
        return requestLogRepository.findAll().stream()
                .filter(log -> log.getEndpoint().getId().equals(endpoint.getId()))
                .toList();
    }

    private RequestLog singleLogOf(Endpoint endpoint) {
        List<RequestLog> logs = logsOf(endpoint);
        assertThat(logs).hasSize(1);
        return logs.getFirst();
    }
}
