package com.hooklens.response;

import com.hooklens.endpoint.Endpoint;
import com.hooklens.endpoint.EndpointService;
import com.hooklens.global.exception.BusinessException;
import com.hooklens.global.exception.ErrorCode;
import com.hooklens.response.dto.ResponseSettingsRequest;
import com.hooklens.response.dto.ResponseSettingsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 엔드포인트의 기본 응답 설정 조회·저장 (REQ-RES-001).
 * 헤더 Map ↔ JSON 문자열 변환은 JsonMapper(Spring 빈)가 필요해서 엔티티·DTO가 아니라 여기서 한다.
 */
@Service                 // 서비스 계층 빈으로 등록
@RequiredArgsConstructor // final 필드 → 생성자 주입
public class ResponseSettingsService {

    /**
     * HTTP 규격이 헤더 이름에 허용하는 문자만: 영문·숫자·일부 기호. 공백·콜론(:)·줄바꿈은 안 된다.
     * 허용 목록(화이트리스트) 방식 — 위험 문자를 하나하나 막는 것보다 빠뜨릴 위험이 없다.
     * static final: 정규식 해석(compile)은 비용이 있어 클래스 로딩 때 한 번만 만들어 재사용한다.
     */
    private static final Pattern HEADER_NAME = Pattern.compile("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$");

    private final EndpointService endpointService;               // 주인 확인 (getOwnedBySlug)
    private final ResponseRuleRepository responseRuleRepository; // 규칙 조회·저장
    private final JsonMapper jsonMapper;                         // 헤더 Map ↔ JSON 문자열 변환

    /** 지금 설정을 돌려준다. 아직 설정 전이면 웹훅에 실제로 나가는 기본 응답(custom: false). */
    @Transactional(readOnly = true)
    public ResponseSettingsResponse get(String slug, String anonymousToken) {
        Endpoint endpoint = endpointService.getOwnedBySlug(slug, anonymousToken); // 주인 확인 (401/403/404/410)
        return responseRuleRepository.findByEndpointIdAndIsDefaultTrue(endpoint.getId())
                .map(this::toResponse)                         // 규칙이 있으면 → 저장된 설정
                .orElseGet(ResponseSettingsResponse::defaults); // 없으면 → 기본 응답
    }

    /**
     * 설정을 저장한다 — 규칙이 있으면 바꾸고(UPDATE), 없으면 만든다(INSERT). upsert.
     * PUT이라 같은 요청을 여러 번 보내도 규칙은 1개(멱등). 주인 확인·헤더 검사를 통과해야 DB를 건드린다.
     * 한계: 같은 엔드포인트에 처음 저장하는 요청이 동시에 두 개 오면 규칙이 두 개 생길 수 있다(1차는 드문 경우로 둔다).
     */
    @Transactional
    public ResponseSettingsResponse save(String slug, String anonymousToken, ResponseSettingsRequest request) {
        Endpoint endpoint = endpointService.getOwnedBySlug(slug, anonymousToken); // ① 주인 확인
        validateHeaders(request.headers());                                      // ② 헤더 주입 검사 (실패하면 400)
        String headersJson = toJson(request.headers());                          // ③ Map → JSON 문자열

        Optional<ResponseRule> existing = responseRuleRepository.findByEndpointIdAndIsDefaultTrue(endpoint.getId());
        ResponseRule rule;
        if (existing.isPresent()) {
            // 있으면 바꾼다. save()를 다시 부르지 않아도 트랜잭션이 끝날 때 변경 감지로 UPDATE된다.
            // map() 대신 if-else: 값을 바꾸는 일이 변환(map) 안에 숨지 않게 드러낸다.
            rule = existing.get();
            rule.update(request.statusCode(), headersJson, request.body(), request.delayMs());
        } else {
            // 없으면 새로 만든다. 새 엔티티는 JPA가 아직 모르니 save()로 알려 줘야 INSERT된다.
            rule = responseRuleRepository.save(ResponseRule.createDefault(
                    endpoint, request.statusCode(), headersJson, request.body(), request.delayMs()));
        }
        return toResponse(rule); // 저장된 결과를 그대로 돌려줘 화면이 바로 폼을 갱신할 수 있게
    }

    /**
     * 헤더 주입(HTTP 응답 분할) 방지. 사용자가 입력한 헤더는 웹훅 응답에 그대로 들어가므로,
     * 값에 줄바꿈이 있으면 "X-A: x\r\nSet-Cookie: ..."처럼 공격자가 헤더를 끼워 넣을 수 있다.
     * 통과해야만 저장한다. 실패하면 어느 헤더가 왜 틀렸는지 메시지에 담아 400.
     */
    private void validateHeaders(Map<String, String> headers) {
        if (headers == null) {
            return; // 헤더를 안 보냈으면 검사할 게 없다
        }
        headers.forEach((name, value) -> {
            // 이름: 처음(^)부터 끝($)까지 허용 문자로만 되어 있어야 한다
            if (name == null || !HEADER_NAME.matcher(name).matches()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "헤더 이름에 쓸 수 없는 문자가 있습니다: " + name);
            }
            // 값: 공백·콜론·한글 등은 괜찮고, 응답을 쪼개는 데 쓰이는 줄바꿈(\r, \n)만 막는다
            if (value == null || value.contains("\r") || value.contains("\n")) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "헤더 값에 줄바꿈을 넣을 수 없습니다: " + name);
            }
        });
    }

    /** Map → JSON 문자열 (DB에 저장할 모양). 안 보냈거나 빈 {}이면 null로 저장한다. */
    private String toJson(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        return jsonMapper.writeValueAsString(headers); // {"X-Custom":"hello"} 같은 문자열로
    }

    /** 엔티티 → 응답 DTO. DB의 헤더 JSON 문자열을 Map으로 바꿔서 넘긴다. */
    private ResponseSettingsResponse toResponse(ResponseRule rule) {
        Map<String, String> headers = (rule.getResponseHeaders() == null)
                ? Map.of() // 저장된 헤더가 없으면 null 대신 빈 {} → 화면이 null 확인을 안 해도 된다
                : jsonMapper.readValue(rule.getResponseHeaders(), new TypeReference<Map<String, String>>() {});
        return ResponseSettingsResponse.of(rule, headers);
    }
}
