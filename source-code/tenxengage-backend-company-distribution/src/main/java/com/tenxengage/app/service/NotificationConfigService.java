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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class NotificationConfigService {

    private static final Logger log = LoggerFactory.getLogger(NotificationConfigService.class);
    private static final Set<String> CONFIGURABLE_ROLES = Set.of(
        "CLIENT_ADMIN",
        "PARTNER_ADMIN",
        "PARTNER_SELLER"
    );

    private final ClientNotificationRoleConfigRepository configRepository;
    private final NotificationTypeRepository notificationTypeRepository;
    private final ClientRepository clientRepository;
    private final TenantValidator tenantValidator;

    public NotificationConfigService(ClientNotificationRoleConfigRepository configRepository,
                                      NotificationTypeRepository notificationTypeRepository,
                                      ClientRepository clientRepository,
                                      TenantValidator tenantValidator) {
        this.configRepository = configRepository;
        this.notificationTypeRepository = notificationTypeRepository;
        this.clientRepository = clientRepository;
        this.tenantValidator = tenantValidator;
    }

    @Transactional(readOnly = true)
    public List<ClientNotificationConfigResponse> getConfigs() {
        UUID clientId = tenantValidator.getCurrentClientId();
        List<NotificationType> allTypes = notificationTypeRepository.findAllByOrderByCategoryAscKeyAsc();
        Map<UUID, List<ClientNotificationRoleConfig>> overridesByType = configRepository
            .findByClientId(clientId).stream()
            .collect(Collectors.groupingBy(ClientNotificationRoleConfig::getNotificationTypeId));

        List<ClientNotificationConfigResponse> result = new ArrayList<>();
        for (NotificationType type : allTypes) {
            List<ClientNotificationRoleConfig> overrides = overridesByType.getOrDefault(type.getId(), List.of());
            Map<String, Boolean> overrideMap = overrides.stream()
                .collect(Collectors.toMap(ClientNotificationRoleConfig::getRoleName,
                    ClientNotificationRoleConfig::getEnabled));

            Set<String> defaultRoleSet = parseDefaultRoles(type.getDefaultRoles());

            List<ClientNotificationConfigResponse.RoleConfig> roleConfigs = new ArrayList<>();
            for (String role : CONFIGURABLE_ROLES) {
                boolean isDefault = defaultRoleSet.contains(role);
                boolean enabled;
                if (overrideMap.containsKey(role)) {
                    enabled = overrideMap.get(role);
                } else {
                    enabled = isDefault;
                }
                roleConfigs.add(new ClientNotificationConfigResponse.RoleConfig(role, enabled, isDefault));
            }

            result.add(new ClientNotificationConfigResponse(
                type.getId(), type.getKey(), type.getCategory(), type.getTitle(), roleConfigs));
        }
        return result;
    }

    @Transactional
    public ClientNotificationConfigResponse updateConfig(UpdateClientNotificationConfigRequest request) {
        UUID clientId = tenantValidator.getCurrentClientId();
        NotificationType type = notificationTypeRepository.findById(request.notificationTypeId())
            .orElseThrow(() -> new EntityNotFoundException("Notification type not found: " + request.notificationTypeId()));

        if (!CONFIGURABLE_ROLES.contains(request.roleName())) {
            throw new IllegalArgumentException("Invalid role name: " + request.roleName());
        }

        ClientNotificationRoleConfig config = configRepository
            .findByClientIdAndNotificationTypeIdAndRoleName(clientId, type.getId(), request.roleName())
            .orElse(null);

        if (config == null) {
            config = ClientNotificationRoleConfig.builder()
                .clientId(clientId)
                .notificationTypeId(type.getId())
                .roleName(request.roleName())
                .enabled(request.enabled())
                .build();
        } else {
            config.setEnabled(request.enabled());
        }

        configRepository.save(config);
        log.info("Updated notification config: client={}, type={}, role={}, enabled={}",
            clientId, type.getKey(), request.roleName(), request.enabled());

        // Return the full config for this type
        return buildConfigResponse(clientId, type);
    }

    @Transactional(readOnly = true)
    public int getRetentionDays() {
        UUID clientId = tenantValidator.getCurrentClientId();
        Client client = clientRepository.findById(clientId)
            .orElseThrow(() -> new EntityNotFoundException("Client not found"));
        return client.getNotificationRetentionDays();
    }

    @Transactional
    public int updateRetentionDays(UpdateNotificationRetentionRequest request) {
        UUID clientId = tenantValidator.getCurrentClientId();
        Client client = clientRepository.findById(clientId)
            .orElseThrow(() -> new EntityNotFoundException("Client not found"));
        client.setNotificationRetentionDays(request.retentionDays());
        clientRepository.save(client);
        log.info("Updated notification retention for client {}: {} days", clientId, request.retentionDays());
        return request.retentionDays();
    }

    /**
     * Resolves effective roles for a notification type within a client.
     * Returns the set of role names that should receive this notification.
     */
    @Transactional(readOnly = true)
    public Set<String> resolveEffectiveRoles(UUID clientId, UUID notificationTypeId, String defaultRoles) {
        List<ClientNotificationRoleConfig> overrides = configRepository
            .findByClientIdAndNotificationTypeId(clientId, notificationTypeId);

        Map<String, Boolean> overrideMap = overrides.stream()
            .collect(Collectors.toMap(ClientNotificationRoleConfig::getRoleName,
                ClientNotificationRoleConfig::getEnabled));

        Set<String> defaultRoleSet = parseDefaultRoles(defaultRoles);

        return CONFIGURABLE_ROLES.stream()
            .filter(role -> {
                if (overrideMap.containsKey(role)) {
                    return overrideMap.get(role);
                }
                return defaultRoleSet.contains(role);
            })
            .collect(Collectors.toSet());
    }

    private ClientNotificationConfigResponse buildConfigResponse(UUID clientId, NotificationType type) {
        List<ClientNotificationRoleConfig> overrides = configRepository
            .findByClientIdAndNotificationTypeId(clientId, type.getId());
        Map<String, Boolean> overrideMap = overrides.stream()
            .collect(Collectors.toMap(ClientNotificationRoleConfig::getRoleName,
                ClientNotificationRoleConfig::getEnabled));

        Set<String> defaultRoleSet = parseDefaultRoles(type.getDefaultRoles());

        List<ClientNotificationConfigResponse.RoleConfig> roleConfigs = new ArrayList<>();
        for (String role : CONFIGURABLE_ROLES) {
            boolean isDefault = defaultRoleSet.contains(role);
            boolean enabled = overrideMap.containsKey(role) ? overrideMap.get(role) : isDefault;
            roleConfigs.add(new ClientNotificationConfigResponse.RoleConfig(role, enabled, isDefault));
        }

        return new ClientNotificationConfigResponse(
            type.getId(), type.getKey(), type.getCategory(), type.getTitle(), roleConfigs);
    }

    private Set<String> parseDefaultRoles(String defaultRoles) {
        if (defaultRoles == null || defaultRoles.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(defaultRoles.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toSet());
    }
}
