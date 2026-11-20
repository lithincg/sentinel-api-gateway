package com.sentinel.apigateway.security;

import com.sentinel.apigateway.entity.ApiKey;
import com.sentinel.apigateway.entity.User;
import com.sentinel.apigateway.repository.ApiKeyRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthFilterTest {

    @Mock
    private ApiKeyRepository apiKeyRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private ApiKeyAuthFilter filter;

    @BeforeEach
    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static String sha256(String raw) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    @Test
    void shouldNotFilter_skipsPublicPathsAndBearerWithoutApiKey() {
        MockHttpServletRequest healthReq = new MockHttpServletRequest("GET", "/health");
        MockHttpServletRequest authReq = new MockHttpServletRequest("POST", "/api/auth/login");
        MockHttpServletRequest actuatorReq = new MockHttpServletRequest("GET", "/actuator/health");

        MockHttpServletRequest jwtReq = new MockHttpServletRequest("GET", "/api/keys");
        jwtReq.addHeader("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.token");

        MockHttpServletRequest apiKeyReq = new MockHttpServletRequest("GET", "/api/dummy");
        apiKeyReq.addHeader("X-API-KEY", "abcde12345");

        assertTrue(filter.shouldNotFilter(healthReq));
        assertTrue(filter.shouldNotFilter(authReq));
        assertTrue(filter.shouldNotFilter(actuatorReq));
        assertTrue(filter.shouldNotFilter(jwtReq));
        assertFalse(filter.shouldNotFilter(apiKeyReq));
    }

    @Test
    void doFilter_authenticatesFromRedisCacheWithoutTouchingDb() throws Exception {
        String rawKey = "abcde1234567890";
        String hashedKey = sha256(rawKey);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(hashedKey)).thenReturn("cached@sentinel.io");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/dummy");
        request.addHeader("X-API-KEY", rawKey);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertEquals("cached@sentinel.io", auth.getName());
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(apiKeyRepository, passwordEncoder);
    }

    @Test
    void doFilter_verifiesDbAndPopulatesCacheOnCacheMiss() throws Exception {
        String rawKey = "abcde1234567890";
        String hashedKey = sha256(rawKey);
        User user = User.builder().email("db@sentinel.io").build();
        ApiKey apiKey = ApiKey.builder().keyPrefix("abcde").keyHash("$2a$hash").active(true).user(user).build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(hashedKey)).thenReturn(null);
        when(apiKeyRepository.findByKeyPrefixAndActiveTrueWithUser("abcde")).thenReturn(List.of(apiKey));
        when(passwordEncoder.matches(rawKey, "$2a$hash")).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/dummy");
        request.addHeader("X-API-KEY", rawKey);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertEquals("db@sentinel.io", auth.getName());
        verify(valueOperations).set(hashedKey, "db@sentinel.io", 5L, TimeUnit.MINUTES);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_fallsBackToDbAuthWhenRedisIsUnavailable() throws Exception {
        String rawKey = "abcde1234567890";
        User user = User.builder().email("fallback@sentinel.io").build();
        ApiKey apiKey = ApiKey.builder().keyPrefix("abcde").keyHash("$2a$hash").active(true).user(user).build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("Redis unreachable"));
        doThrow(new RuntimeException("Redis unreachable"))
                .when(valueOperations).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
        when(apiKeyRepository.findByKeyPrefixAndActiveTrueWithUser("abcde")).thenReturn(List.of(apiKey));
        when(passwordEncoder.matches(rawKey, "$2a$hash")).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/dummy");
        request.addHeader("X-API-KEY", rawKey);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertEquals("fallback@sentinel.io", auth.getName());
        assertEquals(200, response.getStatus());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_rejectsMissingShortAndInvalidApiKeys() throws Exception {
        MockHttpServletRequest missingReq = new MockHttpServletRequest("GET", "/api/dummy");
        MockHttpServletResponse missingRes = new MockHttpServletResponse();
        filter.doFilter(missingReq, missingRes, filterChain);
        assertEquals(401, missingRes.getStatus());

        MockHttpServletRequest shortReq = new MockHttpServletRequest("GET", "/api/dummy");
        shortReq.addHeader("X-API-KEY", "123");
        MockHttpServletResponse shortRes = new MockHttpServletResponse();
        filter.doFilter(shortReq, shortRes, filterChain);
        assertEquals(401, shortRes.getStatus());

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(apiKeyRepository.findByKeyPrefixAndActiveTrueWithUser("abcde")).thenReturn(List.of());

        MockHttpServletRequest badReq = new MockHttpServletRequest("GET", "/api/dummy");
        badReq.addHeader("X-API-KEY", "abcde_invalid");
        MockHttpServletResponse badRes = new MockHttpServletResponse();
        filter.doFilter(badReq, badRes, filterChain);
        assertEquals(401, badRes.getStatus());

        verify(filterChain, never()).doFilter(any(), any());
    }
}
