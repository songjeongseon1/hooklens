package com.hooklens.response;

import com.hooklens.endpoint.Endpoint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * 웹훅 수신 시 돌려줄 응답 설정 (REQ-HOOK-002).
 * 1차 범위는 엔드포인트당 기본 규칙 하나. 조건부 규칙(priority, match_*)은 P2(REQ-RES-002)에서 추가한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResponseRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rule_id")
    private Long id;

    /** 엔드포인트가 삭제되면 DB가 규칙도 함께 지운다 (FK ON DELETE CASCADE). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "endpoint_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Endpoint endpoint;

    @Column(nullable = false)
    private boolean isDefault;

    @Column(nullable = false)
    private int statusCode;

    /** {"이름": "값"} 형태의 JSON. 없으면 null */
    @Column(columnDefinition = "json")
    private String responseHeaders;

    /** 없으면 null (예: 204 No Content) */
    @Column(columnDefinition = "text")
    private String responseBody;

    /** 응답 전 대기 시간(ms). 0이면 바로 응답 */
    @Column(nullable = false)
    private int delayMs;

    public static ResponseRule createDefault(Endpoint endpoint, int statusCode,
                                             String responseHeaders, String responseBody, int delayMs) {
        ResponseRule rule = new ResponseRule();
        rule.endpoint = endpoint;
        rule.isDefault = true;
        rule.statusCode = statusCode;
        rule.responseHeaders = responseHeaders;
        rule.responseBody = responseBody;
        rule.delayMs = delayMs;
        return rule;
    }
}
