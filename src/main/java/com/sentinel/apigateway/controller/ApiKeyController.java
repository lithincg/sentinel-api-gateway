package com.sentinel.apigateway.controller;

import com.sentinel.apigateway.dto.ApiKeyResponse;
import com.sentinel.apigateway.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

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

    @GetMapping("/keys")
    public ResponseEntity<List<ApiKeyResponse>> listKeys(Principal principal) {
        return ResponseEntity.ok(userService.listApiKeys(principal.getName()));
    }

    @DeleteMapping("/keys/{id}")
    public ResponseEntity<Void> revokeKey(@PathVariable Long id, Principal principal) {
        userService.revokeApiKey(principal.getName(), id);
        return ResponseEntity.noContent().build();
    }
}
