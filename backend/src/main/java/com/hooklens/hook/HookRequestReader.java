package com.hooklens.hook;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 웹훅 요청을 받은 그대로 읽어 CapturedRequest로 만든다.
 */
@Component
@RequiredArgsConstructor
public class HookRequestReader {

    static final String HOOK_PATH_PREFIX = "/h/";

    private final JsonMapper jsonMapper;

    public CapturedRequest read(HttpServletRequest request, String slug) {
        byte[] body = readBody(request);
        return new CapturedRequest(
                request.getMethod(),
                extractSubPath(request, slug),
                request.getQueryString(),
                jsonMapper.writeValueAsString(readHeaders(request)),
                body.length == 0 ? null : new String(body, StandardCharsets.UTF_8),
                request.getContentType(),
                request.getRemoteAddr(),
                body.length
        );
    }

    /**
     * 바디는 스트림이라 한 번만 읽을 수 있다. 그래서 @RequestBody나 getParameter()를 쓰지 않고
     * 여기서 원본 바이트를 직접 읽는다. (같은 이유로 application.yml에서 FormContentFilter를 끈다)
     * 바이너리 바디는 UTF-8 문자열로 바꾸며 깨질 수 있다 — 1차 범위의 한계로 둔다.
     */
    private byte[] readBody(HttpServletRequest request) {
        try {
            return request.getInputStream().readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("웹훅 바디를 읽지 못했습니다.", e);
        }
    }

    /** 들어온 순서를 유지하고, 같은 이름의 헤더 값은 목록으로 모은다. */
    private Map<String, List<String>> readHeaders(HttpServletRequest request) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        for (String name : Collections.list(request.getHeaderNames())) {
            headers.put(name, new ArrayList<>(Collections.list(request.getHeaders(name))));
        }
        return headers;
    }

    /** /h/{slug}/payments/done → "/payments/done", /h/{slug} → "/" (인코딩된 원본 그대로) */
    private String extractSubPath(HttpServletRequest request, String slug) {
        String prefix = request.getContextPath() + HOOK_PATH_PREFIX + slug;
        String uri = request.getRequestURI();
        if (!uri.startsWith(prefix) || uri.length() == prefix.length()) {
            return "/";
        }
        return uri.substring(prefix.length());
    }
}
