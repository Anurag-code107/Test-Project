package com.tenxengage.app.repository;

import com.tenxengage.app.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, UUID> {

    Optional<Permission> findByPermissionKey(String permissionKey);

    List<Permission> findAllByOrderBySortOrderAsc();

    List<Permission> findByPermissionTypeOrderBySortOrderAsc(String permissionType);

    List<Permission> findByScopeInOrderBySortOrderAsc(List<String> scopes);
}
