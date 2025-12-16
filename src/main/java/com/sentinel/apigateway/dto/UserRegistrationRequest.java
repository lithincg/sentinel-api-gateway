package com.sentinel.apigateway.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserRegistrationRequest(
            @NotBlank(message="email required")
            @Email
            String email,
            @NotBlank(message="Password must not be blank")
            @Size(min=8,message = "Password must have atleast 8 characters")
            String password) {

}
