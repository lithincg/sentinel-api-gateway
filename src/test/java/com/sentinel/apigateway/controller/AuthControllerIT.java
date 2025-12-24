package com.sentinel.apigateway.controller;

import com.sentinel.apigateway.BaseIntegrationTest;
import com.sentinel.apigateway.dto.UserRegistrationRequest;
import com.sentinel.apigateway.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

public class AuthControllerIT extends BaseIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void shouldRegisterUserSuccessfully() {
        UserRegistrationRequest request = new UserRegistrationRequest(
                "test@sentinel.com",
                "password123"
        );

        ResponseEntity<User> response = restTemplate.postForEntity(
                "/api/auth/register",
                request,
                User.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getEmail()).isEqualTo("test@sentinel.com");
    }
}