package com.tenxengage.app.repository;

import com.tenxengage.app.entity.ClientRolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ClientRolePermissionRepository extends JpaRepository<ClientRolePermission, UUID> {

    List<ClientRolePermission> findByClientRoleId(UUID clientRoleId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM ClientRolePermission p WHERE p.clientRoleId = :clientRoleId")
    void deleteByClientRoleId(UUID clientRoleId);
}
