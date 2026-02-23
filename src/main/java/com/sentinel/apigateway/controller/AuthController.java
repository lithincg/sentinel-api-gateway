package com.sentinel.apigateway.controller;

import com.sentinel.apigateway.dto.LoginRequest;
import com.sentinel.apigateway.dto.LoginResponse;
import com.sentinel.apigateway.dto.UserRegistrationRequest;
import com.sentinel.apigateway.dto.UserRegistrationResponse;
import com.sentinel.apigateway.entity.User;
import com.sentinel.apigateway.service.JwtUtil;
import com.sentinel.apigateway.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    @PostMapping("/register")
    public ResponseEntity<UserRegistrationResponse> register(@Valid @RequestBody UserRegistrationRequest request) {
        User registeredUser = userService.registerUser(request);
        UserRegistrationResponse response = new UserRegistrationResponse(registeredUser.getId(),
                                            registeredUser.getEmail(),registeredUser.getRole().name());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody @Valid LoginRequest request) {
    LoginResponse loginResponse= new LoginResponse(userService.login(request.email(), request.password()));
    return ResponseEntity.ok(loginResponse);
    }
}