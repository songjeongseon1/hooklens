package com.hooklens.hook;

import com.hooklens.response.ResponseRule;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class HookResponseBuilder {

  static final String DEFAULT_RESPONSE_BODY = "{\"ok\":true}";

  private static final Set<String> IGNORED_HEADERS = Set.of(
      "content-length", "transfer-encoding", "connection",
      "keep-alive", "upgrade", "te", "trailer");

  private final JsonMapper jsonMapper;

  public ResponseEntity<String> build(ResponseRule rule) {
    HttpHeaders headers = new HttpHeaders();
    parseHeaders(rule.getResponseHeaders()).forEach((name, value) -> {
      if (!IGNORED_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
        headers.add(name, value);
      }
    });
    return ResponseEntity.status(rule.getStatusCode())
        .headers(headers)
        .body(rule.getResponseBody());
  }

  public ResponseEntity<String> defaultResponse() {
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .body(DEFAULT_RESPONSE_BODY);
  }

  private Map<String, String> parseHeaders(String json) {
    if (json == null || json.isBlank()) {
      return Map.of();
    }
    return jsonMapper.readValue(json, new TypeReference<Map<String, String>>() {
    });
  }
}
