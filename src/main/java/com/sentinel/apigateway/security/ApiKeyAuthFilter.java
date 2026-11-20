package com.sentinel.apigateway.security;

import com.sentinel.apigateway.repository.ApiKeyRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private final ApiKeyRepository apiKeyRepository;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if(path.startsWith("/health") || path.startsWith("/api/auth/") || path.startsWith("/actuator/") || path.equals("/"))
            return true;
        else{
            String authHeader = request.getHeader("Authorization");
            String rawKey = request.getHeader("X-API-KEY");
            if (authHeader != null && authHeader.startsWith("Bearer ") && rawKey == null )
                return true;
            else
                return false;
        }
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String rawKey = request.getHeader("X-API-KEY");
        if (rawKey == null || rawKey.isBlank()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Missing API Key");
            return;
        }
        if (rawKey.length() < 5) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Invalid API Key Format");
            return;
        }
        String encodedKey;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            encodedKey = HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Internal Security Configuration Error", e);
        }

        String cachedEmail = null;
        try {
            cachedEmail = redisTemplate.opsForValue().get(encodedKey);
        } catch (Exception e) {
            log.warn("Redis cache lookup failed, falling back to DB auth: {}", e.getMessage());
        }
        if (cachedEmail != null) {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(cachedEmail, null, Collections.emptyList())
            );
            filterChain.doFilter(request, response);
            return;
        }

        String prefix = rawKey.substring(0, 5);
        var candidates = apiKeyRepository.findByKeyPrefixAndActiveTrueWithUser(prefix);

        for (var key : candidates) {
            if (passwordEncoder.matches(rawKey, key.getKeyHash())) {
                String email = key.getUser().getEmail();
                try {
                    redisTemplate.opsForValue().set(encodedKey, email, 5, TimeUnit.MINUTES);
                } catch (Exception e) {
                    log.warn("Redis cache write failed after DB auth: {}", e.getMessage());
                }

                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(email, null, Collections.emptyList())
                );
                filterChain.doFilter(request, response);
                return;
            }
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.getWriter().write("Invalid API Key");
    }
}