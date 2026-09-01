package com.tenxengage.app.repository;

import com.tenxengage.app.entity.ClientRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClientRoleRepository extends JpaRepository<ClientRole, UUID> {

    List<ClientRole> findByClientIdOrderByNameAsc(UUID clientId);

    List<ClientRole> findByClientIdAndSystemTrue(UUID clientId);

    Optional<ClientRole> findByClientIdAndBaseRoleNameAndSystemTrue(UUID clientId, String baseRoleName);

    Optional<ClientRole> findByClientIdAndName(UUID clientId, String name);

    boolean existsByClientIdAndName(UUID clientId, String name);

    Optional<ClientRole> findByClientIdAndRoleTypeAndDefaultRoleTrue(UUID clientId, String roleType);
}
