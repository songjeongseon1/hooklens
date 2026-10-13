package com.hooklens.endpoint;

import com.hooklens.endpoint.EndpointService.CreatedEndpoint;
import com.hooklens.endpoint.dto.CreateEndpointRequest;
import com.hooklens.endpoint.dto.CreateEndpointResponse;
import com.hooklens.global.config.HookLensProperties;
import com.hooklens.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/endpoints")
@RequiredArgsConstructor
public class EndpointController {

    private final EndpointService endpointService;
    private final HookLensProperties properties;

    /** 비회원 엔드포인트 발급 (REQ-EP-001). 바디 없이 요청해도 된다. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreateEndpointResponse> create(@RequestBody(required = false) @Valid CreateEndpointRequest request) {
        CreatedEndpoint created = endpointService.createAnonymous(normalizeName(request));
        return ApiResponse.ok(CreateEndpointResponse.of(created, properties.baseUrl()));
    }

    /** 빈 문자열·공백만 있는 이름은 이름 없음(null)으로 저장한다. */
    private String normalizeName(CreateEndpointRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            return null;
        }
        return request.name().strip();
    }
}
