package com.sentinel.apigateway.service;

import com.sentinel.apigateway.entity.User;
import com.sentinel.apigateway.exception.DuplicateUserException;
import com.sentinel.apigateway.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    @Transactional
    public User registerUser(String email, String rawPassword){
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

}