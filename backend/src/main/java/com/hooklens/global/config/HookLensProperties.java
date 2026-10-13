package com.hooklens.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml의 hooklens.* 설정.
 *
 * @param baseUrl 사용자에게 보여줄 웹훅 URL의 앞부분 (예: http://localhost:8080).
 *                요청 정보로 만들면 Nginx 뒤에서 scheme·host가 틀어질 수 있어 설정값으로 둔다.
 */
@ConfigurationProperties(prefix = "hooklens")
public record HookLensProperties(String baseUrl) {
}
