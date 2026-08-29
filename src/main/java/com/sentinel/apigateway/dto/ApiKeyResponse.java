package com.sentinel.apigateway.dto;

import java.time.LocalDateTime;

public record ApiKeyResponse(
        Long id,
        String keyPrefix,
        boolean active,
        LocalDateTime createdAt
) {}
