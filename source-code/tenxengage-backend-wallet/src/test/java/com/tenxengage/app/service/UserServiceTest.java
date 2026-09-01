package com.tenxengage.app.service;

import com.tenxengage.app.dto.response.UserResponse;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.UserStatus;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.security.TenantValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TenantValidator tenantValidator;

    @InjectMocks
    private UserService userService;

    private User testUser;
    private UUID clientId;

    @BeforeEach
    void setUp() {
        clientId = UUID.randomUUID();

        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setEmail("john.doe@example.com");
        testUser.setFirstName("John");
        testUser.setLastName("Doe");
        testUser.setPhone("+1234567890");
        testUser.setPasswordHash("$2a$12$hashedpassword");
        testUser.setStatus(UserStatus.ACTIVE);
        testUser.setClientId(clientId);
        testUser.setCreatedAt(Instant.now());
        testUser.setUpdatedAt(Instant.now());
    }

    @Test
    void getUsersReturnsTenantScopedPage() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<User> userPage = new PageImpl<>(List.of(testUser), pageable, 1);

        when(tenantValidator.isTenxAdmin()).thenReturn(false);
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(userRepository.searchByClientId(eq(clientId), any(), eq(pageable))).thenReturn(userPage);

        Page<UserResponse> result = userService.getUsers(pageable, null, null, null);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).email()).isEqualTo("john.doe@example.com");
        verify(userRepository).searchByClientId(eq(clientId), any(), eq(pageable));
    }

    @Test
    void getUsersReturnsAllForTenxAdmin() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<User> userPage = new PageImpl<>(List.of(testUser), pageable, 1);

        when(tenantValidator.isTenxAdmin()).thenReturn(true);
        when(userRepository.searchUsers(any(), eq(pageable))).thenReturn(userPage);

        Page<UserResponse> result = userService.getUsers(pageable, null, null, null);

        assertThat(result.getContent()).hasSize(1);
        verify(userRepository).searchUsers(any(), eq(pageable));
    }

    @Test
    void getUserByIdReturnsTenantScopedUser() {
        UUID userId = testUser.getId();
        when(tenantValidator.isTenxAdmin()).thenReturn(false);
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(userRepository.findByIdAndClientId(userId, clientId)).thenReturn(Optional.of(testUser));

        UserResponse result = userService.getUserById(userId);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(userId);
        assertThat(result.email()).isEqualTo("john.doe@example.com");
        verify(userRepository).findByIdAndClientId(userId, clientId);
    }

    @Test
    void getUserByIdThrowsWhenNotFound() {
        UUID userId = UUID.randomUUID();
        when(tenantValidator.isTenxAdmin()).thenReturn(false);
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(userRepository.findByIdAndClientId(userId, clientId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(userId))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteUserSucceeds() {
        UUID userId = testUser.getId();
        when(tenantValidator.isTenxAdmin()).thenReturn(false);
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(userRepository.findByIdAndClientId(userId, clientId)).thenReturn(Optional.of(testUser));

        userService.deleteUser(userId);

        verify(userRepository).delete(testUser);
    }

    @Test
    void deleteUserThrowsWhenNotInTenant() {
        UUID userId = UUID.randomUUID();
        when(tenantValidator.isTenxAdmin()).thenReturn(false);
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(userRepository.findByIdAndClientId(userId, clientId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deleteUser(userId))
            .isInstanceOf(ResourceNotFoundException.class);
    }
}
