package com.hooklens.response;

import com.hooklens.TestcontainersConfiguration;
import com.hooklens.endpoint.AnonymousTokenProvider;
import com.hooklens.endpoint.Endpoint;
import com.hooklens.endpoint.EndpointRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET·PUT /api/endpoints/{slug}/response 기본 응답 설정 통합 테스트 (Docker 필요).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ResponseSettingsApiTest {

    private static final String TOKEN = "test-anonymous-token";

    /** 정상 설정: 201, XML 헤더 2개, XML 바디, 지연 0 */
    private static final String VALID_SETTINGS = """
            {
              "statusCode": 201,
              "headers": { "Content-Type": "application/xml", "X-Custom": "hello" },
              "body": "<result>accepted</result>",
              "delayMs": 0
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EndpointRepository endpointRepository;

    @Autowired
    private ResponseRuleRepository responseRuleRepository;

    @Autowired
    private AnonymousTokenProvider tokenProvider;

    @Autowired
    private Clock clock;

    @Test
    void 설정_전에는_기본_응답과_custom_false를_돌려준다() throws Exception {
        Endpoint endpoint = saveEndpoint();

        mockMvc.perform(get(url(endpoint)).header("X-Anonymous-Token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statusCode").value(200))
                .andExpect(jsonPath("$.data.body").value("{\"ok\":true}")) // 웹훅에 실제로 나가는 기본 바디
                .andExpect(jsonPath("$.data.delayMs").value(0))
                .andExpect(jsonPath("$.data.custom").value(false));        // 아직 사용자가 설정하지 않음
    }

    @Test
    void 저장하면_그_값이_응답되고_조회에도_반영된다() throws Exception {
        Endpoint endpoint = saveEndpoint();

        // PUT 응답에 저장된 값이 그대로 온다 (화면이 바로 폼을 갱신할 수 있게)
        mockMvc.perform(put(url(endpoint))
                        .header("X-Anonymous-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_SETTINGS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statusCode").value(201))
                .andExpect(jsonPath("$.data.headers['X-Custom']").value("hello")) // 헤더가 문자열이 아니라 객체로
                .andExpect(jsonPath("$.data.custom").value(true));

        // 다시 GET해도 같은 값
        mockMvc.perform(get(url(endpoint)).header("X-Anonymous-Token", TOKEN))
                .andExpect(jsonPath("$.data.statusCode").value(201))
                .andExpect(jsonPath("$.data.headers['Content-Type']").value("application/xml"))
                .andExpect(jsonPath("$.data.body").value("<result>accepted</result>"))
                .andExpect(jsonPath("$.data.custom").value(true));
    }

    @Test
    void 두_번_저장해도_규칙은_하나이고_마지막_값으로_바뀐다() throws Exception {
        // PUT은 멱등: 첫 번째는 INSERT, 두 번째는 UPDATE → 규칙이 두 개 생기지 않아야 한다
        Endpoint endpoint = saveEndpoint();

        putSettings(endpoint, VALID_SETTINGS);
        putSettings(endpoint, """
                { "statusCode": 202, "body": "second", "delayMs": 100 }
                """);

        long ruleCount = responseRuleRepository.findAll().stream()
                .filter(rule -> rule.getEndpoint().getId().equals(endpoint.getId()))
                .count();
        assertThat(ruleCount).isEqualTo(1);

        mockMvc.perform(get(url(endpoint)).header("X-Anonymous-Token", TOKEN))
                .andExpect(jsonPath("$.data.statusCode").value(202))
                .andExpect(jsonPath("$.data.body").value("second"))
                .andExpect(jsonPath("$.data.delayMs").value(100))
                .andExpect(jsonPath("$.data.headers").isEmpty()); // 헤더를 안 보냈으니 빈 {} (이전 헤더가 남지 않는다 = "통째로 바꾸기")
    }

    @Test
    void 저장한_설정으로_실제_웹훅이_응답한다() throws Exception {
        // REQ-RES-001(설정) → REQ-HOOK-002(규칙대로 응답)가 이어지는지 끝까지 확인
        Endpoint endpoint = saveEndpoint();
        putSettings(endpoint, VALID_SETTINGS);

        mockMvc.perform(post("/h/" + endpoint.getSlug()).content("ping"))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Custom", "hello"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(content().string("<result>accepted</result>"));
    }

    @ParameterizedTest(name = "statusCode={0}, delayMs={1} → 400")
    @CsvSource({"99, 0", "600, 0", "200, -1", "200, 5001"})
    void 범위를_벗어난_상태_코드나_지연이면_400(int statusCode, int delayMs) throws Exception {
        Endpoint endpoint = saveEndpoint();

        mockMvc.perform(put(url(endpoint))
                        .header("X-Anonymous-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statusCode\": " + statusCode + ", \"delayMs\": " + delayMs + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void 헤더_이름에_공백이_있으면_400이고_어느_헤더인지_알려준다() throws Exception {
        Endpoint endpoint = saveEndpoint();

        mockMvc.perform(put(url(endpoint))
                        .header("X-Anonymous-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "statusCode": 200, "headers": { "X Bad": "v" }, "delayMs": 0 }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.error.message").value(containsString("X Bad"))); // 3-1에서 만든 메시지 생성자
    }

    @Test
    void 헤더_값에_줄바꿈이_있으면_400이고_저장되지_않는다() throws Exception {
        // 헤더 주입(HTTP 응답 분할) 시도: 값에 \r\n을 넣어 Set-Cookie 헤더를 끼워 넣으려 한다
        Endpoint endpoint = saveEndpoint();

        mockMvc.perform(put(url(endpoint))
                        .header("X-Anonymous-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        // 자바 텍스트 블록에서 \\r\\n → JSON 문자열의 \r\n 이스케이프 → 실제 값은 줄바꿈 문자
                        .content("""
                                { "statusCode": 200, "headers": { "X-Evil": "a\\r\\nSet-Cookie: session=hacked" }, "delayMs": 0 }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value(containsString("X-Evil")));

        // 검사에서 막혔으니 규칙이 저장되지 않았다 → 여전히 기본 응답
        mockMvc.perform(get(url(endpoint)).header("X-Anonymous-Token", TOKEN))
                .andExpect(jsonPath("$.data.custom").value(false));
    }

    @Test
    void 토큰이_없으면_저장할_수_없다_401() throws Exception {
        Endpoint endpoint = saveEndpoint();

        mockMvc.perform(put(url(endpoint))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_SETTINGS))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void 남의_토큰으로는_조회할_수_없다_403() throws Exception {
        Endpoint endpoint = saveEndpoint();

        mockMvc.perform(get(url(endpoint)).header("X-Anonymous-Token", "someone-elses-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN_ENDPOINT"));
    }

    private String url(Endpoint endpoint) {
        return "/api/endpoints/" + endpoint.getSlug() + "/response";
    }

    /** 설정을 저장하고 200인지만 확인한다 (준비 단계용) */
    private void putSettings(Endpoint endpoint, String json) throws Exception {
        mockMvc.perform(put(url(endpoint))
                        .header("X-Anonymous-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());
    }

    /** TOKEN으로 관리할 수 있는 비회원 엔드포인트 */
    private Endpoint saveEndpoint() {
        String slug = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        return endpointRepository.save(
                Endpoint.createAnonymous(slug, null, tokenProvider.hash(TOKEN), LocalDateTime.now(clock)));
    }
}
