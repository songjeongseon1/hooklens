package com.hooklens.log;

import com.hooklens.global.response.ApiResponse;
import com.hooklens.log.dto.RequestLogDetail;
import com.hooklens.log.dto.RequestLogPage;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/endpoints/{slug}/requests")
@RequiredArgsConstructor
public class RequestLogController {

    private final RequestLogService requestLogService;

    /**
     * 요청 목록 최신순 커서 조회 (REQ-LOG-002).
     * 토큰 헤더는 required = false로 받는다. 없을 때 Spring 기본 예외 대신 우리 형식의 401로 응답하기 위해서다.
     */
    @GetMapping
    public ApiResponse<RequestLogPage> list(
            @PathVariable String slug,
            @RequestHeader(value = "X-Anonymous-Token", required = false) String anonymousToken,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size는 1~100 사이여야 합니다.")
            @Max(value = 100, message = "size는 1~100 사이여야 합니다.") int size) {
        return ApiResponse.ok(requestLogService.getPage(slug, anonymousToken, cursor, size));
    }

    /** 요청 상세 조회 (REQ-LOG-003). 남의 엔드포인트 요청 id면 404 */
    @GetMapping("/{requestId}")
    public ApiResponse<RequestLogDetail> detail(
            @PathVariable String slug,
            @PathVariable Long requestId,
            @RequestHeader(value = "X-Anonymous-Token", required = false) String anonymousToken) {
        return ApiResponse.ok(requestLogService.getDetail(slug, anonymousToken, requestId));
    }
}
