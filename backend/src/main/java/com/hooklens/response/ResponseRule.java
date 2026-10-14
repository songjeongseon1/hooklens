package com.hooklens.response;

import com.hooklens.endpoint.Endpoint;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResponseRule {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "rule_id")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "endpoint_id", nullable = false)
  @OnDelete(action = OnDeleteAction.CASCADE)
  private Endpoint endpoint;

  @Column(nullable = false)
  private boolean isDefault;

  @Column(nullable = false)
  private int statusCode;

  @Column(columnDefinition = "json")
  private String responseHeaders;

  @Column(columnDefinition = "text")
  private String responseBody;

  @Column(nullable = false)
  private int delayMs;

  public static ResponseRule createDefault(Endpoint endpoint, int statusCode, String responseHeaders, String responseBody, int delayMs){
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
