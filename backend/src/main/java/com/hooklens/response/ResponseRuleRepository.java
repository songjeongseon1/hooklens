package com.hooklens.response;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ResponseRuleRepository extends JpaRepository<ResponseRule, Long> {

    /** 엔드포인트의 기본 응답 규칙. 없으면 기본 응답 200 {"ok":true}를 쓴다. */
    Optional<ResponseRule> findByEndpointIdAndIsDefaultTrue(Long endpointId);
}
