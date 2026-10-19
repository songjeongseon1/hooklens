package com.hooklens.endpoint;

import com.hooklens.global.exception.BusinessException;
import com.hooklens.global.exception.ErrorCode;
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
     * slug로 사용 가능한 엔드포인트를 찾는다. 웹훅 수신 등 slug로 접근하는 곳은 모두 이 메서드를 쓴다.
     * 만료된 비회원 엔드포인트는 보관 정책 스케줄러가 지우기 전까지 410, 지운 뒤에는 404가 된다.
     */
    @Transactional(readOnly = true)
    public Endpoint getActiveBySlug(String slug) {
        Endpoint endpoint = endpointRepository.findBySlug(slug)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENDPOINT_NOT_FOUND));
        if (endpoint.isExpired(LocalDateTime.now(clock))) {
            throw new BusinessException(ErrorCode.ENDPOINT_EXPIRED);
        }
        return endpoint;
    }

    /**
     * slug의 엔드포인트를 찾고, 익명 토큰으로 주인인지 확인한다. 관리 API는 모두 이 메서드를 쓴다.
     * 토큰이 없으면 401, 틀리면 403. 없는 slug·만료는 getActiveBySlug와 같다(404·410).
     * 토큰 확인을 먼저 하는 이유: 가장 싼 확인이라, 토큰이 없으면 DB를 조회할 필요가 없다.
     */
    @Transactional(readOnly = true)
    public Endpoint getOwnedBySlug(String slug, String anonymousToken) {
        if (anonymousToken == null || anonymousToken.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        Endpoint endpoint = getActiveBySlug(slug);
        if (!tokenProvider.matches(anonymousToken, endpoint.getAnonymousTokenHash())) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ENDPOINT);
        }
        return endpoint;
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
