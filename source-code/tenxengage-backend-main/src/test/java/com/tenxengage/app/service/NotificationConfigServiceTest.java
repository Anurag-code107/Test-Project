package com.tenxengage.app.service;

import com.tenxengage.app.dto.request.UpdateClientNotificationConfigRequest;
import com.tenxengage.app.dto.request.UpdateNotificationRetentionRequest;
import com.tenxengage.app.dto.response.ClientNotificationConfigResponse;
import com.tenxengage.app.entity.Client;
import com.tenxengage.app.entity.ClientNotificationRoleConfig;
import com.tenxengage.app.entity.NotificationType;
import com.tenxengage.app.repository.ClientNotificationRoleConfigRepository;
import com.tenxengage.app.repository.ClientRepository;
import com.tenxengage.app.repository.NotificationTypeRepository;
import com.tenxengage.app.security.TenantValidator;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationConfigServiceTest {

    @Mock private ClientNotificationRoleConfigRepository configRepository;
    @Mock private NotificationTypeRepository notificationTypeRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private TenantValidator tenantValidator;

    @InjectMocks private NotificationConfigService service;

    private UUID clientId;

    @BeforeEach
    void setUp() {
        clientId = UUID.randomUUID();
    }

    // -------------------------------------------------------------------------
    // getConfigs
    // -------------------------------------------------------------------------

    @Test
    void getConfigs_noOverrides_returnsDefaultRoleEnabledStates() {
        NotificationType type = buildNotificationType("INCENTIVE_COMPLETED", "Incentive",
                "Incentive Completed", "CLIENT_ADMIN,PARTNER_ADMIN");

        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(notificationTypeRepository.findAllByOrderByCategoryAscKeyAsc())
                .thenReturn(List.of(type));
        when(configRepository.findByClientId(clientId)).thenReturn(List.of());

        List<ClientNotificationConfigResponse> result = service.getConfigs();

        assertThat(result).hasSize(1);
        ClientNotificationConfigResponse response = result.get(0);
        assertThat(response.notificationTypeKey()).isEqualTo("INCENTIVE_COMPLETED");
        // CLIENT_ADMIN should be enabled by default
        boolean clientAdminEnabled = response.roleConfigs().stream()
                .filter(r -> r.roleName().equals("CLIENT_ADMIN"))
                .findFirst()
                .map(ClientNotificationConfigResponse.RoleConfig::enabled)
                .orElse(false);
        assertThat(clientAdminEnabled).isTrue();
    }

    @Test
    void getConfigs_withOverride_appliesOverrideValue() {
        UUID typeId = UUID.randomUUID();
        NotificationType type = buildNotTypeWithId(typeId, "SOME_NOTIF", "General",
                "Notification Title", "CLIENT_ADMIN");

        ClientNotificationRoleConfig override = ClientNotificationRoleConfig.builder()
                .clientId(clientId)
                .notificationTypeId(typeId)
                .roleName("CLIENT_ADMIN")
                .enabled(false)
                .build();
        override.setId(UUID.randomUUID());
        override.setCreatedAt(Instant.now());
        override.setUpdatedAt(Instant.now());

        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(notificationTypeRepository.findAllByOrderByCategoryAscKeyAsc())
                .thenReturn(List.of(type));
        when(configRepository.findByClientId(clientId)).thenReturn(List.of(override));

        List<ClientNotificationConfigResponse> result = service.getConfigs();

        assertThat(result).hasSize(1);
        boolean clientAdminEnabled = result.get(0).roleConfigs().stream()
                .filter(r -> r.roleName().equals("CLIENT_ADMIN"))
                .findFirst()
                .map(ClientNotificationConfigResponse.RoleConfig::enabled)
                .orElse(true);
        assertThat(clientAdminEnabled).isFalse();
    }

    // -------------------------------------------------------------------------
    // updateConfig
    // -------------------------------------------------------------------------

    @Test
    void updateConfig_newConfig_createsEntry() {
        UUID typeId = UUID.randomUUID();
        NotificationType type = buildNotTypeWithId(typeId, "TEST_KEY", "Category",
                "Test Notification", "CLIENT_ADMIN");

        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(notificationTypeRepository.findById(typeId)).thenReturn(Optional.of(type));
        when(configRepository.findByClientIdAndNotificationTypeIdAndRoleName(
                clientId, typeId, "CLIENT_ADMIN")).thenReturn(Optional.empty());
        when(configRepository.save(any(ClientNotificationRoleConfig.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(configRepository.findByClientIdAndNotificationTypeId(clientId, typeId))
                .thenReturn(List.of());

        UpdateClientNotificationConfigRequest request =
                new UpdateClientNotificationConfigRequest(typeId, "CLIENT_ADMIN", true);

        ClientNotificationConfigResponse result = service.updateConfig(request);

        assertThat(result).isNotNull();
        verify(configRepository).save(any(ClientNotificationRoleConfig.class));
    }

    @Test
    void updateConfig_typeNotFound_throwsEntityNotFoundException() {
        UUID typeId = UUID.randomUUID();
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(notificationTypeRepository.findById(typeId)).thenReturn(Optional.empty());

        UpdateClientNotificationConfigRequest request =
                new UpdateClientNotificationConfigRequest(typeId, "CLIENT_ADMIN", false);

        assertThatThrownBy(() -> service.updateConfig(request))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // getRetentionDays / updateRetentionDays
    // -------------------------------------------------------------------------

    @Test
    void getRetentionDays_clientExists_returnsValue() {
        Client client = buildClient(clientId);
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));

        int days = service.getRetentionDays();

        assertThat(days).isEqualTo(90);
    }

    @Test
    void updateRetentionDays_clientExists_updatesAndReturnsNewValue() {
        Client client = buildClient(clientId);
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(clientRepository.save(any(Client.class))).thenReturn(client);

        UpdateNotificationRetentionRequest request = new UpdateNotificationRetentionRequest(30);
        int result = service.updateRetentionDays(request);

        assertThat(result).isEqualTo(30);
        assertThat(client.getNotificationRetentionDays()).isEqualTo(30);
        verify(clientRepository).save(client);
    }

    // -------------------------------------------------------------------------
    // resolveEffectiveRoles
    // -------------------------------------------------------------------------

    @Test
    void resolveEffectiveRoles_noOverrides_returnsDefaultRoles() {
        UUID typeId = UUID.randomUUID();
        when(configRepository.findByClientIdAndNotificationTypeId(clientId, typeId))
                .thenReturn(List.of());

        Set<String> roles = service.resolveEffectiveRoles(clientId, typeId, "CLIENT_ADMIN,PARTNER_ADMIN");

        assertThat(roles).containsExactlyInAnyOrder("CLIENT_ADMIN", "PARTNER_ADMIN");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private NotificationType buildNotificationType(String key, String category,
                                                    String title, String defaultRoles) {
        UUID id = UUID.randomUUID();
        return buildNotTypeWithId(id, key, category, title, defaultRoles);
    }

    private NotificationType buildNotTypeWithId(UUID id, String key, String category,
                                                 String title, String defaultRoles) {
        NotificationType type = NotificationType.builder()
                .key(key)
                .category(category)
                .title(title)
                .defaultRoles(defaultRoles)
                .build();
        type.setId(id);
        type.setCreatedAt(Instant.now());
        type.setUpdatedAt(Instant.now());
        return type;
    }

    private Client buildClient(UUID id) {
        Client client = Client.builder()
                .name("Test Client")
                .subdomain("test")
                .notificationRetentionDays(90)
                .build();
        client.setId(id);
        client.setCreatedAt(Instant.now());
        client.setUpdatedAt(Instant.now());
        return client;
    }
}
