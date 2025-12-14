package com.sentinel.apigateway.repository;

import com.sentinel.apigateway.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
public class UserRepositoryTest {

    private final User testUser = User.builder()
            .id(2L)
            .email("test@email.com")
            .passwordHash("testHash")
            .role(User.Role.USER)
            .build();

    @Mock
    private UserRepository userRepository;

    @Test
    void shouldReturnUser_whenEmailExists() {
        when(userRepository.findByEmail("test@email.com"))
                .thenReturn(Optional.of(testUser));

        Optional<User> result = userRepository.findByEmail("test@email.com");

        assertTrue(result.isPresent());
        assertEquals("test@email.com", result.get().getEmail());
        assertEquals(User.Role.USER, result.get().getRole());
        verify(userRepository, times(1)).findByEmail("test@email.com");
    }

    @Test
    void shouldReturnEmpty_whenEmailNotFound() {
        String nonexistentEmail = "unknown@email.com";

        when(userRepository.findByEmail(nonexistentEmail))
                .thenReturn(Optional.empty());

        Optional<User> result = userRepository.findByEmail(nonexistentEmail);

        assertFalse(result.isPresent());
        verify(userRepository, times(1)).findByEmail(nonexistentEmail);
    }
}