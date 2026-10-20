package com.hooklens.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @Scheduled(정해진 주기로 메서드 실행)를 켠다.
 * 지금은 SSE 하트비트(REQ-LOG-001), 나중에 로그 정리 스케줄러(REQ-LOG-005)도 이걸 쓴다.
 */
@Configuration    // 설정 클래스라고 Spring에 알린다
@EnableScheduling // 이게 있어야 @Scheduled가 동작한다 (없으면 붙여도 아무 일도 안 일어남)
public class SchedulingConfig {
}
