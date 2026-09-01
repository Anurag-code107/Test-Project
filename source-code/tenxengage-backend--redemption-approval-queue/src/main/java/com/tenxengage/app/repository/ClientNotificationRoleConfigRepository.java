package com.tenxengage.app.repository;

import com.tenxengage.app.entity.ClientNotificationRoleConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClientNotificationRoleConfigRepository extends JpaRepository<ClientNotificationRoleConfig, UUID> {

    List<ClientNotificationRoleConfig> findByClientId(UUID clientId);

    List<ClientNotificationRoleConfig> findByClientIdAndNotificationTypeId(UUID clientId, UUID notificationTypeId);

    Optional<ClientNotificationRoleConfig> findByClientIdAndNotificationTypeIdAndRoleName(
        UUID clientId, UUID notificationTypeId, String roleName);
}
