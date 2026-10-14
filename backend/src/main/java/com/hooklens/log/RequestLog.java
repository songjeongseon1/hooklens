package com.hooklens.log;

import com.hooklens.endpoint.Endpoint;
import com.hooklens.hook.CapturedRequest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/**
 * 엔드포인트로 들어온 웹훅 요청 하나 (REQ-HOOK-001).
 * 받은 그대로 보여주는 것이 목적이라, 값을 가공하지 않고 원본에 가깝게 저장한다.
 */
@Entity
@Table(name = "request_log", indexes = {
        // 엔드포인트별 최신순 커서 조회(REQ-LOG-002)용
        @Index(name = "idx_request_log_endpoint", columnList = "endpoint_id, request_id DESC"),
        // 보관 기간이 지난 로그 삭제(REQ-LOG-005)용
        @Index(name = "idx_request_log_received", columnList = "received_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RequestLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    private Long id;

    /** 엔드포인트가 삭제되면 DB가 로그도 함께 지운다 (FK ON DELETE CASCADE). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "endpoint_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Endpoint endpoint;

    @Column(nullable = false, length = 10)
    private String method;

    /** slug 뒤의 경로. 예: /h/{slug}/payments → "/payments", 하위 경로가 없으면 "/" */
    @Column(nullable = false, length = 2048)
    private String path;

    /** 물음표 뒤 원본 쿼리 문자열. 없으면 null */
    @Column(columnDefinition = "text")
    private String queryString;

    /** {"헤더 이름": ["값1", "값2"]} 형태의 JSON. 같은 이름의 헤더가 여러 번 올 수 있어 값은 목록이다. */
    @Column(nullable = false, columnDefinition = "json")
    private String headers;

    /** 바디 원문. 없으면 null */
    @Column(columnDefinition = "mediumtext")
    private String body;

    @Column(length = 255)
    private String contentType;

    /** IPv6까지 담을 수 있는 길이 */
    @Column(nullable = false, length = 45)
    private String ip;

    @Column(nullable = false)
    private int sizeBytes;

    @Column(nullable = false)
    private LocalDateTime receivedAt;

    public static RequestLog of(Endpoint endpoint, CapturedRequest captured, LocalDateTime receivedAt) {
        RequestLog log = new RequestLog();
        log.endpoint = endpoint;
        log.method = captured.method();
        log.path = captured.path();
        log.queryString = captured.queryString();
        log.headers = captured.headersJson();
        log.body = captured.body();
        log.contentType = captured.contentType();
        log.ip = captured.ip();
        log.sizeBytes = captured.sizeBytes();
        log.receivedAt = receivedAt;
        return log;
    }
}
