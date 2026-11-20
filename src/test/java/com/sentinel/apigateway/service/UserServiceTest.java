package com.sentinel.apigateway.service;

import com.sentinel.apigateway.dto.UserRegistrationRequest;
import com.sentinel.apigateway.entity.ApiKey;
import com.sentinel.apigateway.entity.User;
import com.sentinel.apigateway.exception.ApiKeyNotFoundException;
import com.sentinel.apigateway.exception.DuplicateUserException;
import com.sentinel.apigateway.exception.InvalidCredentialsException;
import com.sentinel.apigateway.repository.ApiKeyRepository;
import com.sentinel.apigateway.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ApiKeyRepository apiKeyRepository;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private StringRedisTemplate redisTemplate;

    @InjectMocks
    private UserService userService;

    @Test
    void registerUser_savesEncodedPasswordAndUserRole() {
        UserRegistrationRequest request = new UserRegistrationRequest("dev@sentinel.io", "secret123");
        when(userRepository.findByEmail("dev@sentinel.io")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret123")).thenReturn("$2a$encoded");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User saved = userService.registerUser(request);

        assertEquals("dev@sentinel.io", saved.getEmail());
        assertEquals("$2a$encoded", saved.getPasswordHash());
        assertEquals(User.Role.USER, saved.getRole());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void registerUser_throwsWhenEmailAlreadyExists() {
        UserRegistrationRequest request = new UserRegistrationRequest("dup@sentinel.io", "secret123");
        when(userRepository.findByEmail("dup@sentinel.io")).thenReturn(Optional.of(new User()));

        assertThrows(DuplicateUserException.class, () -> userService.registerUser(request));
        verify(userRepository, never()).save(any());
    }

    @Test
    void login_returnsJwtWhenCredentialsMatch() {
        User user = User.builder().email("dev@sentinel.io").passwordHash("$2a$hash").role(User.Role.USER).build();
        when(userRepository.findByEmail("dev@sentinel.io")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret123", "$2a$hash")).thenReturn(true);
        when(jwtUtil.generateToken("dev@sentinel.io")).thenReturn("jwt-token");

        String token = userService.login("dev@sentinel.io", "secret123");

        assertEquals("jwt-token", token);
    }

    @Test
    void login_throwsWhenPasswordInvalid() {
        User user = User.builder().email("dev@sentinel.io").passwordHash("$2a$hash").role(User.Role.USER).build();
        when(userRepository.findByEmail("dev@sentinel.io")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "$2a$hash")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> userService.login("dev@sentinel.io", "wrong"));
        verifyNoInteractions(jwtUtil);
    }

    @Test
    void generateApiKey_persistsBcryptAndSha256LookupWithoutRawKey() throws Exception {
        User user = User.builder().id(1L).email("dev@sentinel.io").passwordHash("$2a$hash").role(User.Role.USER).build();
        when(userRepository.findByEmail("dev@sentinel.io")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$bcrypt-key");

        String rawKey = userService.generateApiKey("dev@sentinel.io");

        assertNotNull(rawKey);
        assertEquals(32, rawKey.length());

        ArgumentCaptor<ApiKey> captor = ArgumentCaptor.forClass(ApiKey.class);
        verify(apiKeyRepository).save(captor.capture());
        ApiKey persisted = captor.getValue();

        String expectedLookupHash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(rawKey.getBytes(StandardCharsets.UTF_8))
        );

        assertEquals(rawKey.substring(0, 5), persisted.getKeyPrefix());
        assertEquals("$2a$bcrypt-key", persisted.getKeyHash());
        assertEquals(expectedLookupHash, persisted.getLookupHash());
        assertNotEquals(rawKey, persisted.getKeyHash());
        assertNotEquals(rawKey, persisted.getLookupHash());
        assertSame(user, persisted.getUser());
    }

    @Test
    void revokeApiKey_deactivatesAndEvictsRedisForOwner() {
        User owner = User.builder().id(1L).email("owner@sentinel.io").build();
        ApiKey key = ApiKey.builder().id(42L).active(true).lookupHash("sha256-hash").user(owner).build();
        when(apiKeyRepository.findById(42L)).thenReturn(Optional.of(key));

        userService.revokeApiKey("owner@sentinel.io", 42L);

        assertFalse(key.getActive());
        verify(apiKeyRepository).save(key);
        verify(redisTemplate).delete("sha256-hash");
    }

    @Test
    void revokeApiKey_throwsWhenOwnedByDifferentUser() {
        User owner = User.builder().id(1L).email("owner@sentinel.io").build();
        ApiKey key = ApiKey.builder().id(42L).active(true).lookupHash("sha256-hash").user(owner).build();
        when(apiKeyRepository.findById(42L)).thenReturn(Optional.of(key));

        assertThrows(ApiKeyNotFoundException.class, () -> userService.revokeApiKey("other@sentinel.io", 42L));

        assertTrue(key.getActive());
        verify(apiKeyRepository, never()).save(any());
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void revokeApiKey_deactivatesInDbEvenWhenRedisEvictionFails() {
        User owner = User.builder().id(1L).email("owner@sentinel.io").build();
        ApiKey key = ApiKey.builder().id(42L).active(true).lookupHash("sha256-hash").user(owner).build();
        when(apiKeyRepository.findById(42L)).thenReturn(Optional.of(key));
        when(redisTemplate.delete("sha256-hash")).thenThrow(new RuntimeException("Redis down"));

        assertDoesNotThrow(() -> userService.revokeApiKey("owner@sentinel.io", 42L));

        assertFalse(key.getActive());
        verify(apiKeyRepository).save(key);
    }
}
