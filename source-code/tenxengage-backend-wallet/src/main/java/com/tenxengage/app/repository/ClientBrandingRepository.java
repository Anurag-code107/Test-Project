package com.tenxengage.app.repository;

import com.tenxengage.app.entity.ClientBranding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClientBrandingRepository extends JpaRepository<ClientBranding, UUID> {

    Optional<ClientBranding> findByClientId(UUID clientId);
}
