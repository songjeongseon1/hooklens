package com.hooklens.endpoint;

import com.hooklens.endpoint.EndpointService.CreatedEndpoint;
import com.hooklens.global.config.TimeConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EndpointServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 13, 17, 0);

    @Mock
    private EndpointRepository endpointRepository;

    @Mock
    private SlugGenerator slugGenerator;

    private final AnonymousTokenProvider tokenProvider = new AnonymousTokenProvider();

    private EndpointService endpointService;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(ZonedDateTime.of(NOW, TimeConfig.ZONE).toInstant(), TimeConfig.ZONE);
        endpointService = new EndpointService(endpointRepository, slugGenerator, tokenProvider, fixedClock);
    }

    @Test
    void 비회원_엔드포인트는_24시간_뒤_만료되고_토큰은_해시로만_저장된다() {
        given(slugGenerator.generate()).willReturn("aZ3kP9xQ2m");
        given(endpointRepository.existsBySlug("aZ3kP9xQ2m")).willReturn(false);
        given(endpointRepository.save(any(Endpoint.class))).willAnswer(invocation -> invocation.getArgument(0));

        CreatedEndpoint created = endpointService.createAnonymous("결제 테스트");

        Endpoint endpoint = created.endpoint();
        assertThat(endpoint.getSlug()).isEqualTo("aZ3kP9xQ2m");
        assertThat(endpoint.getName()).isEqualTo("결제 테스트");
        assertThat(endpoint.getMemberId()).isNull();
        assertThat(endpoint.getCreatedAt()).isEqualTo(NOW);
        assertThat(endpoint.getExpiresAt()).isEqualTo(NOW.plusHours(24));
        assertThat(endpoint.getAnonymousTokenHash())
                .isEqualTo(tokenProvider.hash(created.anonymousToken()))
                .isNotEqualTo(created.anonymousToken());
    }

    @Test
    void slug가_중복되면_새로_만들어_재시도한다() {
        given(slugGenerator.generate()).willReturn("duplicate1", "unique0001");
        given(endpointRepository.existsBySlug("duplicate1")).willReturn(true);
        given(endpointRepository.existsBySlug("unique0001")).willReturn(false);
        given(endpointRepository.save(any(Endpoint.class))).willAnswer(invocation -> invocation.getArgument(0));

        CreatedEndpoint created = endpointService.createAnonymous(null);

        assertThat(created.endpoint().getSlug()).isEqualTo("unique0001");
    }

    @Test
    void slug가_최대_시도_횟수만큼_연속_중복되면_예외가_발생한다() {
        given(slugGenerator.generate()).willReturn("duplicate1");
        given(endpointRepository.existsBySlug(anyString())).willReturn(true);

        assertThatThrownBy(() -> endpointService.createAnonymous(null))
                .isInstanceOf(IllegalStateException.class);
        verify(endpointRepository, never()).save(any());
    }
}
