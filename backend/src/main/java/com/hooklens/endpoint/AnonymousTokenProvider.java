package com.hooklens.endpoint;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 비회원이 자기 엔드포인트를 관리할 때 쓰는 익명 토큰.
 * 원문은 발급 응답에서 한 번만 내려주고, DB에는 SHA-256 해시만 저장한다.
 *
 * BCrypt가 아닌 SHA-256을 쓰는 이유: BCrypt는 추측 가능한 비밀번호를 느리게 만들어 막는 용도다.
 * 이 토큰은 32바이트 난수라 무차별 대입이 불가능하므로 빠른 해시로 충분하고, 같은 값이면 해시도 같아 바로 조회할 수 있다.
 */
@Component
public class AnonymousTokenProvider {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom random = new SecureRandom();

    /** URL·헤더에 그대로 넣을 수 있는 Base64URL 문자열(패딩 없음, 43자) */
    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // 모든 JVM이 SHA-256을 지원해야 하므로 실제로는 발생하지 않음
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }
}
