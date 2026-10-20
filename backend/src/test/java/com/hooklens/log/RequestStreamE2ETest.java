package com.hooklens.log;

import com.hooklens.TestcontainersConfiguration;
import com.hooklens.endpoint.AnonymousTokenProvider;
import com.hooklens.endpoint.Endpoint;
import com.hooklens.endpoint.EndpointRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 끝에서 끝까지(E2E): 진짜 서버를 띄워 스트림에 연결한 상태에서 웹훅을 보내면, 스트림으로 그 요청이 도착하는가.
 * MockMvc는 끝나지 않는 응답을 실시간으로 읽기 어려워서, 이 테스트만 실제 포트 + 자바 내장 HttpClient를 쓴다.
 * 이벤트 발행 → 커밋 → AFTER_COMMIT 리스너 → Registry 전송까지 전부 실제로 돈다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT) // 진짜 톰캣을 빈 포트에 띄운다
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class RequestStreamE2ETest {

    private static final String TOKEN = "test-anonymous-token";

    @LocalServerPort
    private int port; // RANDOM_PORT로 정해진 실제 포트 번호

    @Autowired
    private EndpointRepository endpointRepository;

    @Autowired
    private AnonymousTokenProvider tokenProvider;

    @Autowired
    private Clock clock;

    private final HttpClient httpClient = HttpClient.newHttpClient(); // 자바 11+ 내장 HTTP 클라이언트 (의존성 추가 없음)

    @Test
    void 스트림에_연결한_상태에서_웹훅이_오면_그_요청이_스트림으로_도착한다() throws Exception {
        Endpoint endpoint = saveEndpoint();
        String baseUrl = "http://localhost:" + port;

        // 1. 스트림에 연결한다. ofLines(): 응답 바디를 "줄 단위로 계속 흘러나오는 Stream"으로 받는다
        HttpRequest streamRequest = HttpRequest.newBuilder(URI.create(baseUrl + "/api/endpoints/" + endpoint.getSlug() + "/stream"))
                .header("X-Anonymous-Token", TOKEN)
                .build();
        HttpResponse<Stream<String>> stream = httpClient.send(streamRequest, HttpResponse.BodyHandlers.ofLines());
        assertThat(stream.statusCode()).isEqualTo(200);
        assertThat(stream.headers().firstValue("Content-Type")).hasValueSatisfying(
                type -> assertThat(type).startsWith("text/event-stream")); // SSE 응답 표시
        Iterator<String> lines = stream.body().iterator(); // 다음 줄이 올 때까지 기다렸다가 하나씩 꺼낸다

        // 2. 연결 직후 인사 메시지("event:connected")를 받을 때까지 기다린다 → 확실히 등록된 뒤에 웹훅을 보내기 위해
        readUntil(lines, line -> line.startsWith("event:connected"));

        // 3. 외부 서비스인 척 웹훅을 보낸다
        HttpRequest webhook = HttpRequest.newBuilder(URI.create(baseUrl + "/h/" + endpoint.getSlug() + "/e2e-path"))
                .POST(HttpRequest.BodyPublishers.ofString("{\"hello\":\"sse\"}"))
                .build();
        assertThat(httpClient.send(webhook, HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(200);

        // 4. 스트림으로 "event:request" 다음에 그 요청의 요약(data: JSON)이 와야 한다
        readUntil(lines, line -> line.startsWith("event:request"));
        String data = readUntil(lines, line -> line.startsWith("data:"));
        assertThat(data).contains("\"path\":\"/e2e-path\"").contains("\"method\":\"POST\"");
    }

    /**
     * 조건에 맞는 줄이 나올 때까지 읽고 그 줄을 돌려준다. 5초 안에 안 오면 실패.
     * iterator.next()는 다음 줄이 올 때까지 무한정 기다리므로, 다른 스레드에서 읽고 시간 제한을 건다.
     */
    private String readUntil(Iterator<String> lines, Predicate<String> condition) throws Exception {
        return CompletableFuture.supplyAsync(() -> {
            while (lines.hasNext()) {
                String line = lines.next();
                if (condition.test(line)) {
                    return line;
                }
            }
            throw new IllegalStateException("스트림이 조건에 맞는 줄 없이 끝났다");
        }).get(5, TimeUnit.SECONDS); // 5초 넘으면 TimeoutException → 테스트 실패 (무한 대기 방지)
    }

    /** TOKEN으로 관리할 수 있는 비회원 엔드포인트 */
    private Endpoint saveEndpoint() {
        String slug = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        return endpointRepository.save(
                Endpoint.createAnonymous(slug, null, tokenProvider.hash(TOKEN), LocalDateTime.now(clock)));
    }
}
