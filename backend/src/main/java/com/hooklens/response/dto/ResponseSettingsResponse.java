package com.hooklens.response.dto;

import com.hooklens.hook.HookResponseBuilder;
import com.hooklens.response.ResponseRule;

import java.util.Map;

/**
 * 기본 응답 설정 조회·저장 결과 (GET·PUT /api/endpoints/{slug}/response, REQ-RES-001).
 * 칸 이름이 그대로 JSON 키가 된다. 앞의 네 칸은 요청 DTO와 같은 모양 → 화면이 받은 값을 폼에 채우고 고쳐서 그대로 다시 보낸다.
 *
 * @param headers {"이름": "값"} — DB의 JSON 문자열을 Map으로 바꿔 JSON 객체로 내보낸다
 * @param custom  true = 사용자가 설정함 / false = 아직 설정 안 해서 기본 응답 사용 중 (서버가 규칙 유무로 계산)
 */
public record ResponseSettingsResponse(
        int statusCode,
        Map<String, String> headers,
        String body,
        int delayMs,
        boolean custom
) {

    /** 설정 전: 웹훅에 실제로 나가는 기본 응답(HookResponseBuilder.defaultResponse)과 같은 값을 보여 준다. */
    public static ResponseSettingsResponse defaults() {
        return new ResponseSettingsResponse(
                200,                                        // 기본 상태 코드
                Map.of("Content-Type", "application/json"), // 기본 응답은 JSON
                HookResponseBuilder.DEFAULT_RESPONSE_BODY,  // {"ok":true} — 같은 값을 두 곳에 쓰지 않는다(DRY)
                0,                                          // 지연 없음
                false);                                     // 아직 사용자가 설정하지 않음
    }

    /** 설정 후: 저장된 규칙 + 서비스가 JSON 문자열에서 바꿔 준 헤더 Map (변환에는 JsonMapper 빈이 필요해서 서비스가 한다). */
    public static ResponseSettingsResponse of(ResponseRule rule, Map<String, String> headers) {
        return new ResponseSettingsResponse(
                rule.getStatusCode(),
                headers,
                rule.getResponseBody(),
                rule.getDelayMs(),
                true); // 사용자가 설정한 값
    }
}
