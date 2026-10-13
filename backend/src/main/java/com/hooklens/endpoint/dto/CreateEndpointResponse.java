package com.hooklens.endpoint.dto;

import com.hooklens.endpoint.Endpoint;
import com.hooklens.endpoint.EndpointService.CreatedEndpoint;
import com.hooklens.global.config.TimeConfig;

import java.time.OffsetDateTime;

/**
 * @param anonymousToken 이 응답에서만 한 번 내려준다. 이후 관리 API는 X-Anonymous-Token 헤더로 보낸다.
 */
public record CreateEndpointResponse(
        String slug,
        String url,
        String name,
        String anonymousToken,
        OffsetDateTime expiresAt
) {

    public static CreateEndpointResponse of(CreatedEndpoint created, String baseUrl) {
        Endpoint endpoint = created.endpoint();
        return new CreateEndpointResponse(
                endpoint.getSlug(),
                baseUrl + "/h/" + endpoint.getSlug(),
                endpoint.getName(),
                created.anonymousToken(),
                endpoint.getExpiresAt().atZone(TimeConfig.ZONE).toOffsetDateTime()
        );
    }
}
