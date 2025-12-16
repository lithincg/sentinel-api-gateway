package com.sentinel.apigateway.service;

import com.sentinel.apigateway.dto.UserRegistrationRequest;
import com.sentinel.apigateway.entity.ApiKey;
import com.sentinel.apigateway.entity.User;
import com.sentinel.apigateway.exception.DuplicateUserException;
import com.sentinel.apigateway.exception.UserNotFoundException;
import com.sentinel.apigateway.repository.ApiKeyRepository;
import com.sentinel.apigateway.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final ApiKeyRepository apiKeyRepository;
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
                                .user(user.get())
                                .build();
            apiKeyRepository.save(apiKey);
            return clearKey;
        }


        throw new UserNotFoundException("User not found");

    }

}