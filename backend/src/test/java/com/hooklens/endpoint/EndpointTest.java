package com.hooklens.endpoint;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class EndpointTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 13, 17, 0);
    private static final LocalDateTime EXPIRES_AT = CREATED_AT.plusHours(24);

    private final Endpoint endpoint = Endpoint.createAnonymous("aZ3kP9xQ2m", null, "hash", CREATED_AT);

    @Test
    void 만료_시각_1초_전에는_만료되지_않았다() {
        assertThat(endpoint.isExpired(EXPIRES_AT.minusSeconds(1))).isFalse();
    }

    @Test
    void 만료_시각_정각부터_만료된다() {
        assertThat(endpoint.isExpired(EXPIRES_AT)).isTrue();
    }

    @Test
    void 만료_시각이_지나면_만료된다() {
        assertThat(endpoint.isExpired(EXPIRES_AT.plusSeconds(1))).isTrue();
    }
}
