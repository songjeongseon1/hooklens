package com.hooklens;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mysql.MySQLContainer;

/**
 * 통합 테스트용 실제 MySQL 컨테이너.
 * H2 같은 인메모리 DB는 MySQL과 문법·동작이 달라, 운영과 같은 DB로 검증한다.
 * @ServiceConnection이 컨테이너의 접속 정보를 spring.datasource.*에 자동으로 넣어준다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    MySQLContainer mysqlContainer() {
        return new MySQLContainer("mysql:8.0");
    }
}
