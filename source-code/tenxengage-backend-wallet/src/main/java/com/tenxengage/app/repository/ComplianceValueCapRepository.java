package com.tenxengage.app.repository;

import com.tenxengage.app.entity.ComplianceValueCap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ComplianceValueCapRepository extends JpaRepository<ComplianceValueCap, UUID> {

    Optional<ComplianceValueCap> findByCountryCodeAndClientId(String countryCode, UUID clientId);

    Optional<ComplianceValueCap> findByCountryCodeAndClientIdIsNull(String countryCode);

    List<ComplianceValueCap> findByClientIdIsNull();
}
