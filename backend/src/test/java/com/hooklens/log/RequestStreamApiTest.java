package com.hooklens.log;

import com.hooklens.TestcontainersConfiguration;
import com.hooklens.endpoint.AnonymousTokenProvider;
import com.hooklens.endpoint.Endpoint;
import com.hooklens.endpoint.EndpointRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/endpoints/{slug}/stream 구독·인증 테스트 (Docker 필요).
 * 실제로 메시지가 도착하는지는 RequestStreamE2ETest에서 진짜 서버로 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class RequestStreamApiTest {

    private static final String TOKEN = "test-anonymous-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EndpointRepository endpointRepository;

    @Autowired
    private AnonymousTokenProvider tokenProvider;

    @Autowired
    private RequestStreamRegistry registry; // 연결이 목록에 들어갔는지 확인용

    @Autowired
    private Clock clock;

    @Test
    void 토큰이_맞으면_스트림이_열리고_연결이_등록된다() throws Exception {
        Endpoint endpoint = saveEndpoint();

        mockMvc.perform(get("/api/endpoints/" + endpoint.getSlug() + "/stream")
                        .header("X-Anonymous-Token", TOKEN))
                .andExpect(status().isOk())
                // asyncStarted: 응답이 끝나지 않고 "열린 채로" 넘어갔다 = SSE 스트림이 시작됐다
                .andExpect(request().asyncStarted());

        // 연결이 엔드포인트별 목록에 1개 들어갔다
        assertThat(registry.connectionCount(endpoint.getId())).isEqualTo(1);
    }

    @Test
    void 토큰이_없으면_401이고_연결이_등록되지_않는다() throws Exception {
        Endpoint endpoint = saveEndpoint();

        mockMvc.perform(get("/api/endpoints/" + endpoint.getSlug() + "/stream")) // 헤더 없음
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED")); // 에러는 평소처럼 공통 JSON 형식

        assertThat(registry.connectionCount(endpoint.getId())).isZero(); // 주인 확인에서 막혀 연결이 안 생겼다
    }

    @Test
    void 토큰이_틀리면_403이고_연결이_등록되지_않는다() throws Exception {
        Endpoint endpoint = saveEndpoint();

        mockMvc.perform(get("/api/endpoints/" + endpoint.getSlug() + "/stream")
                        .header("X-Anonymous-Token", "someone-elses-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN_ENDPOINT"));

        assertThat(registry.connectionCount(endpoint.getId())).isZero();
    }

    /** TOKEN으로 관리할 수 있는 비회원 엔드포인트 */
    private Endpoint saveEndpoint() {
        String slug = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        return endpointRepository.save(
                Endpoint.createAnonymous(slug, null, tokenProvider.hash(TOKEN), LocalDateTime.now(clock)));
    }
}
