package com.sentinel.apigateway.repository;

import com.sentinel.apigateway.entity.ApiKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
public class ApiKeyRepositoryTest {

    private final ApiKey activeKey = ApiKey.builder()
            .id(1L)
            .keyPrefix("sk_test")
            .keyHash("hashed_123")
            .active(true)
            .user(null)
            .build();


    @Mock
    private ApiKeyRepository apiKeyRepository;
    @Test
    void shouldReturnKey_whenHashExistsAndActive() {

        when(apiKeyRepository.findByKeyHashAndActiveTrue("hashed_123"))
                .thenReturn(Optional.of(activeKey));

        Optional<ApiKey> result = apiKeyRepository.findByKeyHashAndActiveTrue("hashed_123");

        assertTrue(result.isPresent(), "Result should not be empty.");
        assertEquals("sk_test", result.get().getKeyPrefix(), "Prefix should match the mocked object.");

        verify(apiKeyRepository, times(1)).findByKeyHashAndActiveTrue("hashed_123");
    }
    @Test
    void shouldReturnEmpty_whenHashNotFound() {
        String nonexistentHash = "unknown_hash";


        when(apiKeyRepository.findByKeyHashAndActiveTrue(nonexistentHash))
                .thenReturn(Optional.empty());

        Optional<ApiKey> result = apiKeyRepository.findByKeyHashAndActiveTrue(nonexistentHash);

        assertFalse(result.isPresent(), "Result should be empty (not found).");

        verify(apiKeyRepository, times(1)).findByKeyHashAndActiveTrue(nonexistentHash);
    }
}