package com.sentinel.apigateway.service;

import com.sentinel.apigateway.dto.ApiKeyResponse;
import com.sentinel.apigateway.dto.LoginResponse;
import com.sentinel.apigateway.dto.UserRegistrationRequest;
import com.sentinel.apigateway.entity.ApiKey;
import com.sentinel.apigateway.entity.User;
import com.sentinel.apigateway.exception.ApiKeyNotFoundException;
import com.sentinel.apigateway.exception.DuplicateUserException;
import com.sentinel.apigateway.exception.InvalidCredentialsException;
import com.sentinel.apigateway.exception.UserNotFoundException;
import com.sentinel.apigateway.repository.ApiKeyRepository;
import com.sentinel.apigateway.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApiKeyRepository apiKeyRepository;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;

    @Transactional
    public User registerUser(UserRegistrationRequest userRegistrationRequest) {
        String email=userRegistrationRequest.email();
        String rawPassword=userRegistrationRequest.password();
        if(userRepository.findByEmail(email).isPresent()){
            throw new DuplicateUserException("Email already exists");
        }
        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .role(User.Role.USER)
                .build();
        userRepository.save(user);
        return user;
    }
    @Transactional
    public String generateApiKey(String email){
        Optional<User> user = userRepository.findByEmail(email);
        if(user.isPresent()){
            var clearKey = UUID.randomUUID().toString().replace("-", "");
            var hashedKey = passwordEncoder.encode(clearKey);
            var previewKey = clearKey.substring(0, 5);
            ApiKey apiKey = ApiKey
                                .builder()
                                .keyHash(hashedKey)
                                .keyPrefix(previewKey)
                                .lookupHash(sha256Hex(clearKey))
                                .user(user.get())
                                .build();
            apiKeyRepository.save(apiKey);
            return clearKey;
        }


        throw new UserNotFoundException("User not found");

    }

    @Transactional(readOnly = true)
    public List<ApiKeyResponse> listApiKeys(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        return apiKeyRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(k -> new ApiKeyResponse(k.getId(), k.getKeyPrefix(), k.getActive(), k.getCreatedAt()))
                .toList();
    }

    @Transactional
    public void revokeApiKey(String email, Long keyId) {
        ApiKey key = apiKeyRepository.findById(keyId)
                .filter(k -> k.getUser().getEmail().equals(email))
                .orElseThrow(() -> new ApiKeyNotFoundException("API key not found"));

        key.setActive(false);
        apiKeyRepository.save(key);

        if (key.getLookupHash() != null) {
            redisTemplate.delete(key.getLookupHash());
        }
    }

    private String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Internal Security Configuration Error", e);
        }
    }

    public String login(String email, String password){
        Optional<User> user = userRepository.findByEmail(email);
        if(user.isPresent() && passwordEncoder.matches(password, user.get().getPasswordHash())){
            return jwtUtil.generateToken(user.get().getEmail());

        }
        throw new InvalidCredentialsException("Invalid username or password");
    }

}