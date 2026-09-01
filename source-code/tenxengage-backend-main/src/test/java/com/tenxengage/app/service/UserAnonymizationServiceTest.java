package com.tenxengage.app.service;

import com.tenxengage.app.entity.ClientRole;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.AuditAction;
import com.tenxengage.app.entity.enums.AuditResourceType;
import com.tenxengage.app.entity.enums.UserStatus;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.AuditLogRepository;
import com.tenxengage.app.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAnonymizationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private UserAnonymizationService userAnonymizationService;

    private User testUser;
    private UUID userId;
    private UUID clientId;
    private UUID clientRoleId;
    private ClientRole clientAdminClientRole;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        clientId = UUID.randomUUID();
        clientRoleId = UUID.randomUUID();

        clientAdminClientRole = ClientRole.builder()
                .clientId(clientId).name("Client Admin").system(true)
                .baseRoleName("CLIENT_ADMIN").build();
        clientAdminClientRole.setId(clientRoleId);

        testUser = new User();
        testUser.setId(userId);
        testUser.setEmail("john.doe@example.com");
        testUser.setFirstName("John");
        testUser.setLastName("Doe");
        testUser.setPhone("+1234567890");
        testUser.setAvatar("https://cdn.example.com/avatar.png");
        testUser.setPasswordHash("$2a$12$hashedpassword");
        testUser.setStatus(UserStatus.ACTIVE);
        testUser.setClientId(clientId);
        testUser.setMetadata("{\"theme\":\"dark\"}");
        testUser.setCountryCode("US");
        testUser.setCreatedAt(Instant.now());
        testUser.setUpdatedAt(Instant.now());
    }

    // -------------------------------------------------------------------------
    // anonymizeUser - success path
    // -------------------------------------------------------------------------

    @Test
    void anonymizeUser_replacesPiiWithPlaceholders() {
        when(userRepository.findByIdAndClientId(userId, clientId))
                .thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(auditLogRepository.anonymizeByActorId(userId)).thenReturn(3);

        userAnonymizationService.anonymizeUser(userId, clientId);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User anonymized = captor.getValue();

        assertThat(anonymized.getEmail()).startsWith("anonymized-");
        assertThat(anonymized.getEmail()).endsWith("@deleted.tenxengage.com");
        assertThat(anonymized.getFirstName()).isEqualTo("Deleted");
        assertThat(anonymized.getLastName()).isEqualTo("User");
        assertThat(anonymized.getPhone()).isNull();
        assertThat(anonymized.getAvatar()).isNull();
        assertThat(anonymized.getPasswordHash()).isEqualTo("$ANONYMIZED$");
        assertThat(anonymized.getMetadata()).isEqualTo("{}");
        assertThat(anonymized.getStatus()).isEqualTo(UserStatus.ANONYMIZED);
        assertThat(anonymized.getCountryCode()).isNull();
    }

    @Test
    void anonymizeUser_anonymizesAuditLogEntries() {
        when(userRepository.findByIdAndClientId(userId, clientId))
                .thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(auditLogRepository.anonymizeByActorId(userId)).thenReturn(5);

        userAnonymizationService.anonymizeUser(userId, clientId);

        verify(auditLogRepository).anonymizeByActorId(userId);
    }

    @Test
    void anonymizeUser_logsAnonymizationAction() {
        when(userRepository.findByIdAndClientId(userId, clientId))
                .thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(auditLogRepository.anonymizeByActorId(userId)).thenReturn(0);

        userAnonymizationService.anonymizeUser(userId, clientId);

        verify(auditLogService).log(
                eq(AuditAction.ANONYMIZED),
                eq(AuditResourceType.USER),
                eq(userId),
                eq("Deleted User"),
                any(String.class),
                isNull()
        );
    }

    // -------------------------------------------------------------------------
    // anonymizeUser - error paths
    // -------------------------------------------------------------------------

    @Test
    void anonymizeUser_throwsForUserNotFound() {
        when(userRepository.findByIdAndClientId(userId, clientId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> userAnonymizationService.anonymizeUser(userId, clientId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void anonymizeUser_throwsForAlreadyAnonymized() {
        testUser.setStatus(UserStatus.ANONYMIZED);
        when(userRepository.findByIdAndClientId(userId, clientId))
                .thenReturn(Optional.of(testUser));

        assertThatThrownBy(() -> userAnonymizationService.anonymizeUser(userId, clientId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already anonymized");
    }

    @Test
    void anonymizeUser_throwsForClientAdmin() {
        // CLIENT_ADMIN: user has a clientRole with baseRoleName "CLIENT_ADMIN"
        testUser.setClientRoleId(clientRoleId);
        testUser.setClientRole(clientAdminClientRole);
        when(userRepository.findByIdAndClientId(userId, clientId))
                .thenReturn(Optional.of(testUser));

        assertThatThrownBy(() -> userAnonymizationService.anonymizeUser(userId, clientId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("admin roles");
    }

    @Test
    void anonymizeUser_throwsForTenxAdmin() {
        // TENX_ADMIN: no clientId
        testUser.setClientId(null);
        when(userRepository.findByIdAndClientId(userId, clientId))
                .thenReturn(Optional.of(testUser));

        assertThatThrownBy(() -> userAnonymizationService.anonymizeUser(userId, clientId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("admin roles");
    }

    // -------------------------------------------------------------------------
    // isAnonymizable
    // -------------------------------------------------------------------------

    @Test
    void isAnonymizable_returnsTrueForActiveUser() {
        testUser.setStatus(UserStatus.ACTIVE);
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        boolean result = userAnonymizationService.isAnonymizable(userId);

        assertThat(result).isTrue();
    }

    @Test
    void isAnonymizable_returnsFalseForAnonymizedUser() {
        testUser.setStatus(UserStatus.ANONYMIZED);
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        boolean result = userAnonymizationService.isAnonymizable(userId);

        assertThat(result).isFalse();
    }

    @Test
    void isAnonymizable_returnsFalseForAdmin() {
        // CLIENT_ADMIN: user has a clientRole with baseRoleName "CLIENT_ADMIN"
        testUser.setClientRoleId(clientRoleId);
        testUser.setClientRole(clientAdminClientRole);
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        boolean result = userAnonymizationService.isAnonymizable(userId);

        assertThat(result).isFalse();
    }
}
