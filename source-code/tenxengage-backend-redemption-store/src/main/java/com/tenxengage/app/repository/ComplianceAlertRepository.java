package com.tenxengage.app.repository;

import com.tenxengage.app.entity.ComplianceAlert;
import com.tenxengage.app.entity.enums.ComplianceAlertStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ComplianceAlertRepository extends JpaRepository<ComplianceAlert, UUID> {

    List<ComplianceAlert> findByClientIdAndStatus(UUID clientId, ComplianceAlertStatus status);

    Page<ComplianceAlert> findByClientId(UUID clientId, Pageable pageable);
}
