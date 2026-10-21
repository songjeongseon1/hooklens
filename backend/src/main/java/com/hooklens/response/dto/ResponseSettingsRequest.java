package com.hooklens.response.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * 기본 응답 설정 저장 요청 (PUT /api/endpoints/{slug}/response, REQ-RES-001).
 * 헤더 이름·값의 문자 검사(헤더 주입 방지)는 반복문·정규식이 필요해서 서비스에서 한다.
 *
 * @param headers {"이름": "값"} — 응답에 그대로 들어간다. 없으면 null
 * @param body    응답 바디. 없으면 null (예: 204 No Content)
 */
public record ResponseSettingsRequest(
        @Min(value = 100, message = "statusCode는 100~599 사이여야 합니다.") // HTTP 상태 코드 범위의 시작
        @Max(value = 599, message = "statusCode는 100~599 사이여야 합니다.") // HTTP 상태 코드 범위의 끝
        int statusCode, // JSON에서 빠뜨리면 int 기본값 0 → @Min에 걸려 400 (필수값 누락도 같이 막힌다)

        @Size(max = 20, message = "헤더는 20개까지 설정할 수 있습니다.") // Map에 붙이면 글자 수가 아니라 "항목 개수"를 검사
        Map<String, String> headers,

        @Size(max = 20_000, message = "바디는 20,000자까지 입력할 수 있습니다.") // DB TEXT(64KB) 안에: 한글 3바이트 × 2만 ≈ 60KB
        String body,

        @Min(value = 0, message = "delayMs는 0~5000 사이여야 합니다.")
        @Max(value = 5000, message = "delayMs는 0~5000 사이여야 합니다.") // 너무 길면 웹훅을 보낸 쪽이 타임아웃
        int delayMs
) {
}
