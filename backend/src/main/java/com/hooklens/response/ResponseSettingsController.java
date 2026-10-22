package com.hooklens.response;

import com.hooklens.global.response.ApiResponse;
import com.hooklens.response.dto.ResponseSettingsRequest;
import com.hooklens.response.dto.ResponseSettingsResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 기본 응답 설정 API (REQ-RES-001).
 * 엔드포인트마다 기본 응답은 하나뿐이라 단수형 /response, id도 없다 (여러 개인 요청 목록은 복수형 /requests).
 */
@RestController
@RequestMapping("/api/endpoints/{slug}/response")
@RequiredArgsConstructor // final 필드 → 생성자 주입
public class ResponseSettingsController {

    private final ResponseSettingsService responseSettingsService;

    /** 지금 설정 보기. 설정 전이면 기본 응답(200, {"ok":true})과 custom: false */
    @GetMapping
    public ApiResponse<ResponseSettingsResponse> get(
            @PathVariable String slug,
            @RequestHeader(value = "X-Anonymous-Token", required = false) String anonymousToken) { // 없으면 null → 401
        return ApiResponse.ok(responseSettingsService.get(slug, anonymousToken));
    }

    /**
     * 설정을 통째로 저장한다. PUT = 같은 요청을 여러 번 보내도 결과가 같다(멱등) → 저장 버튼을 두 번 눌러도 규칙은 1개.
     * \@RequestBody: 바디 JSON → DTO. \@Valid: DTO의 @Min·@Max·@Size 검사 (빼먹으면 검사를 아예 안 한다).
     */
    @PutMapping
    public ApiResponse<ResponseSettingsResponse> save(
            @PathVariable String slug,
            @RequestHeader(value = "X-Anonymous-Token", required = false) String anonymousToken,
            @RequestBody @Valid ResponseSettingsRequest request) {
        return ApiResponse.ok(responseSettingsService.save(slug, anonymousToken, request));
    }
}
