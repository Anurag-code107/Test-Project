package com.tenxengage.app.repository;

import com.tenxengage.app.entity.TenantRedemptionSettings;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantRedemptionSettingsRepository extends JpaRepository<TenantRedemptionSettings, UUID> {

    Optional<TenantRedemptionSettings> findByClientId(UUID clientId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM TenantRedemptionSettings s WHERE s.clientId = :clientId")
    Optional<TenantRedemptionSettings> findByClientIdWithLock(@Param("clientId") UUID clientId);
}
