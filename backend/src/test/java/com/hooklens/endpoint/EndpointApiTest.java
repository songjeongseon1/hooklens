package com.hooklens.endpoint;

import com.hooklens.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /api/endpoints → MySQL 저장까지 실제로 확인하는 통합 테스트 (Docker 필요).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class EndpointApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EndpointRepository endpointRepository;

    @Autowired
    private AnonymousTokenProvider tokenProvider;

    @Test
    void 이름을_넣어_비회원_엔드포인트를_발급하면_DB에_저장된다() throws Exception {
        String body = mockMvc.perform(post("/api/endpoints")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"  결제 테스트  \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.slug").value(matchesPattern("^[A-Za-z0-9]{10}$")))
                .andExpect(jsonPath("$.data.name").value("결제 테스트"))
                .andExpect(jsonPath("$.data.anonymousToken").value(matchesPattern("^[A-Za-z0-9_-]{43}$")))
                .andExpect(jsonPath("$.data.expiresAt").value(endsWith("+09:00")))
                .andReturn().getResponse().getContentAsString();

        String slug = JsonPath.read(body, "$.data.slug");
        String token = JsonPath.read(body, "$.data.anonymousToken");
        assertThat(JsonPath.<String>read(body, "$.data.url")).isEqualTo("http://test.hooklens/h/" + slug);

        Endpoint saved = endpointRepository.findBySlug(slug).orElseThrow();
        assertThat(saved.getMemberId()).isNull();
        assertThat(saved.getAnonymousTokenHash()).isEqualTo(tokenProvider.hash(token));
        assertThat(saved.getExpiresAt()).isEqualTo(saved.getCreatedAt().plusHours(24));
    }

    @Test
    void 바디_없이_요청해도_발급된다() throws Exception {
        mockMvc.perform(post("/api/endpoints"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value(nullValue()));
    }

    @Test
    void 이름이_50자를_넘으면_400_INVALID_INPUT() throws Exception {
        mockMvc.perform(post("/api/endpoints")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"" + "가".repeat(51) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void JSON_형식이_틀리면_400_INVALID_INPUT() throws Exception {
        mockMvc.perform(post("/api/endpoints")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }
}
