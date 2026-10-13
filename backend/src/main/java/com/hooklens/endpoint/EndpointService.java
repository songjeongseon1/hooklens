package com.hooklens.endpoint;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class EndpointService {

    static final int MAX_SLUG_ATTEMPTS = 3;

    private final EndpointRepository endpointRepository;
    private final SlugGenerator slugGenerator;
    private final AnonymousTokenProvider tokenProvider;
    private final Clock clock;

    /**
     * 비회원 엔드포인트를 발급한다. 반환값의 토큰 원문은 이때만 알 수 있다.
     */
    @Transactional
    public CreatedEndpoint createAnonymous(String name) {
        String token = tokenProvider.generate();
        Endpoint endpoint = Endpoint.createAnonymous(
                generateUniqueSlug(), name, tokenProvider.hash(token), LocalDateTime.now(clock));
        return new CreatedEndpoint(endpointRepository.save(endpoint), token);
    }

    /**
     * 중복 확률은 거의 0이지만 0은 아니므로 미리 확인하고 재시도한다.
     * 확인과 저장 사이에 같은 slug가 끼어드는 경우는 DB 유니크 제약이 최종으로 막는다.
     */
    private String generateUniqueSlug() {
        for (int i = 0; i < MAX_SLUG_ATTEMPTS; i++) {
            String slug = slugGenerator.generate();
            if (!endpointRepository.existsBySlug(slug)) {
                return slug;
            }
        }
        throw new IllegalStateException("slug 생성에 " + MAX_SLUG_ATTEMPTS + "번 연속 실패했습니다.");
    }

    public record CreatedEndpoint(Endpoint endpoint, String anonymousToken) {
    }
}
