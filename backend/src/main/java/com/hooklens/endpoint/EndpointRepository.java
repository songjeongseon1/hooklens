package com.hooklens.endpoint;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EndpointRepository extends JpaRepository<Endpoint, Long> {

    boolean existsBySlug(String slug);

    Optional<Endpoint> findBySlug(String slug);
}
