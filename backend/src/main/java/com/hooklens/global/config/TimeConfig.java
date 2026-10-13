package com.hooklens.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * 현재 시각은 이 Clock에서만 가져온다.
 * LocalDateTime.now()를 직접 부르면 테스트에서 시간을 고정할 수 없기 때문.
 */
@Configuration
public class TimeConfig {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    @Bean
    public Clock clock() {
        return Clock.system(ZONE);
    }
}
