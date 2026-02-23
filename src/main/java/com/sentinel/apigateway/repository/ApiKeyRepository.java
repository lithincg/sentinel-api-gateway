package com.sentinel.apigateway.repository;

import com.sentinel.apigateway.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApiKeyRepository  extends JpaRepository<ApiKey,Long> {
    Optional<ApiKey> findByKeyHashAndActiveTrue(String keyHash);
    List<ApiKey> findByKeyPrefixAndActiveTrue(String keyPrefix);
    @Query("SELECT ak FROM ApiKey ak JOIN FETCH ak.user WHERE ak.keyPrefix = :keyPrefix AND ak.active = true")
    List<ApiKey> findByKeyPrefixAndActiveTrueWithUser(@Param("keyPrefix") String keyPrefix);
}