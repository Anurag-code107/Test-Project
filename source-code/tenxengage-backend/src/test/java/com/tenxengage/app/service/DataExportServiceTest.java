package com.tenxengage.app.service;

import com.tenxengage.app.entity.ConsentRecord;
import com.tenxengage.app.entity.RewardBalance;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.UserLegalAcceptance;
import com.tenxengage.app.entity.enums.ConsentType;
import com.tenxengage.app.entity.enums.UserStatus;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.ConsentRecordRepository;
import com.tenxengage.app.repository.NotificationRepository;
import com.tenxengage.app.repository.RewardBalanceRepository;
import com.tenxengage.app.repository.UserLegalAcceptanceRepository;
import com.tenxengage.app.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataExportServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserLegalAcceptanceRepository userLegalAcceptanceRepository;

    @Mock
    private ConsentRecordRepository consentRecordRepository;

    @Mock
    private RewardBalanceRepository rewardBalanceRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private DataExportService dataExportService;

    private User testUser;
    private UUID userId;
    private UUID clientId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        clientId = UUID.randomUUID();

        testUser = new User();
        testUser.setId(userId);
        testUser.setEmail("jane.doe@example.com");
        testUser.setFirstName("Jane");
        testUser.setLastName("Doe");
        testUser.setPhone("+1234567890");
        testUser.setCountryCode("US");
        testUser.setStatus(UserStatus.ACTIVE);
        testUser.setClientId(clientId);
        testUser.setCreatedAt(Instant.now());
        testUser.setUpdatedAt(Instant.now());
    }

    @Test
    void exportUserData_returnsProfileData() {
        when(userRepository.findByIdAndClientId(userId, clientId)).thenReturn(Optional.of(testUser));
        when(userLegalAcceptanceRepository.findByUserId(userId)).thenReturn(List.of());
        when(consentRecordRepository.findByUserId(userId)).thenReturn(List.of());
        when(rewardBalanceRepository.findByClientIdAndUserId(clientId, userId)).thenReturn(List.of());
        when(notificationRepository.countByClientIdAndUserId(clientId, userId)).thenReturn(0L);

        Map<String, Object> result = dataExportService.exportUserData(userId, clientId);

        assertThat(result).containsKey("profile");
        @SuppressWarnings("unchecked")
        Map<String, Object> profile = (Map<String, Object>) result.get("profile");
        assertThat(profile.get("email")).isEqualTo("jane.doe@example.com");
        assertThat(profile.get("firstName")).isEqualTo("Jane");
        assertThat(profile.get("lastName")).isEqualTo("Doe");
        assertThat(profile.get("countryCode")).isEqualTo("US");
        assertThat(result).containsKey("exportedAt");
    }

    @Test
    @SuppressWarnings("unchecked")
    void exportUserData_includesLegalAcceptances() {
        UserLegalAcceptance acceptance = UserLegalAcceptance.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .clientId(clientId)
                .policyId(UUID.randomUUID())
                .acceptedAt(Instant.now())
                .ipAddress("192.168.1.1")
                .build();

        when(userRepository.findByIdAndClientId(userId, clientId)).thenReturn(Optional.of(testUser));
        when(userLegalAcceptanceRepository.findByUserId(userId)).thenReturn(List.of(acceptance));
        when(consentRecordRepository.findByUserId(userId)).thenReturn(List.of());
        when(rewardBalanceRepository.findByClientIdAndUserId(clientId, userId)).thenReturn(List.of());
        when(notificationRepository.countByClientIdAndUserId(clientId, userId)).thenReturn(0L);

        Map<String, Object> result = dataExportService.exportUserData(userId, clientId);

        List<Map<String, Object>> acceptances = (List<Map<String, Object>>) result.get("legalAcceptances");
        assertThat(acceptances).hasSize(1);
        assertThat(acceptances.get(0).get("ipAddress")).isEqualTo("192.168.1.1");
    }

    @Test
    void exportUserData_throwsForUserNotFound() {
        when(userRepository.findByIdAndClientId(userId, clientId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dataExportService.exportUserData(userId, clientId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void exportUserData_throwsForWrongClient() {
        UUID wrongClientId = UUID.randomUUID();
        when(userRepository.findByIdAndClientId(userId, wrongClientId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dataExportService.exportUserData(userId, wrongClientId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
