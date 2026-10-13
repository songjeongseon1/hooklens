package com.hooklens.endpoint;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * 영숫자 10자 slug 생성. 경우의 수 62^10 ≈ 8.4 × 10^17이라 추측이 사실상 불가능하다.
 * Random은 다음 값을 예측할 수 있으므로 SecureRandom을 쓴다.
 */
@Component
public class SlugGenerator {

    static final int LENGTH = 10;
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
