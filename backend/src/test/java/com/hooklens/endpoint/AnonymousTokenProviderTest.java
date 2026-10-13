package com.hooklens.endpoint;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnonymousTokenProviderTest {

    private final AnonymousTokenProvider tokenProvider = new AnonymousTokenProvider();

    @Test
    void 토큰은_URL에_안전한_43자_문자열이다() {
        assertThat(tokenProvider.generate()).matches("^[A-Za-z0-9_-]{43}$");
    }

    @Test
    void 매번_다른_토큰을_만든다() {
        assertThat(tokenProvider.generate()).isNotEqualTo(tokenProvider.generate());
    }

    @Test
    void SHA_256_해시를_hex_64자로_만든다() {
        // "abc"의 SHA-256 값 (FIPS 180-2 표준 테스트 벡터)
        assertThat(tokenProvider.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
