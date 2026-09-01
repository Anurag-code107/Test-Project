package com.tenxengage.app.repository;

import com.tenxengage.app.entity.ClientPermissionGrant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ClientPermissionGrantRepository extends JpaRepository<ClientPermissionGrant, UUID> {

    List<ClientPermissionGrant> findByClientId(UUID clientId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM ClientPermissionGrant g WHERE g.clientId = :clientId")
    void deleteByClientId(UUID clientId);
}
