package com.sentinel.apigateway.controller;

import com.sentinel.apigateway.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiKeyController {


    private final UserService userService;

    @PostMapping("/keys")
    public ResponseEntity<?> generateKey(Principal principal) {
        String email = principal.getName();
        String apiKey=userService.generateApiKey(email);
        return ResponseEntity.ok().body(apiKey);
    }
}
