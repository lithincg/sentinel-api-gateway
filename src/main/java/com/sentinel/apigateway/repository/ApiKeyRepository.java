package com.sentinel.apigateway.repository;

import com.sentinel.apigateway.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ApiKeyRepository  extends JpaRepository<ApiKey,Long> {
    Optional<ApiKey> findByKeyHashAndActiveTrue(String keyHash);
}