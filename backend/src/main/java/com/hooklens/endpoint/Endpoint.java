package com.hooklens.endpoint;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 웹훅을 받는 고유 URL(/h/{slug}) 하나.
 * 외부에는 숫자 id 대신 slug만 노출한다 (순번이면 남의 엔드포인트를 추측할 수 있음).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Endpoint {

    /** 비회원 엔드포인트 유효 기간 */
    public static final Duration ANONYMOUS_TTL = Duration.ofHours(24);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "endpoint_id")
    private Long id;

    /** NULL이면 비회원. member 테이블이 생기는 3주차(REQ-AUTH-001)에 연관관계로 바꾼다. */
    private Long memberId;

    @Column(nullable = false, unique = true, length = 10)
    private String slug;

    @Column(length = 50)
    private String name;

    /** 익명 토큰의 SHA-256 해시(hex 64자). 원문은 저장하지 않는다. */
    @Column(length = 64)
    private String anonymousTokenHash;

    @Column(length = 2048)
    private String forwardUrl;

    private LocalDateTime expiresAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static Endpoint createAnonymous(String slug, String name, String anonymousTokenHash, LocalDateTime now) {
        Endpoint endpoint = new Endpoint();
        endpoint.slug = slug;
        endpoint.name = name;
        endpoint.anonymousTokenHash = anonymousTokenHash;
        endpoint.createdAt = now;
        endpoint.expiresAt = now.plus(ANONYMOUS_TTL);
        return endpoint;
    }
}
