package com.tenxengage.app.service;

import com.tenxengage.app.entity.ComplianceAlert;
import com.tenxengage.app.entity.enums.ComplianceAlertStatus;
import com.tenxengage.app.entity.enums.ComplianceAlertType;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.repository.ComplianceAlertRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ComplianceAlertService {

    private static final Logger log = LoggerFactory.getLogger(ComplianceAlertService.class);

    private final ComplianceAlertRepository complianceAlertRepository;

    public ComplianceAlertService(ComplianceAlertRepository complianceAlertRepository) {
        this.complianceAlertRepository = complianceAlertRepository;
    }

    @Transactional
    public ComplianceAlert createAlert(UUID clientId, ComplianceAlertType type, String severity,
                                        String description, UUID userId, UUID partnerId,
                                        UUID incentiveId) {
        ComplianceAlert alert = ComplianceAlert.builder()
                .clientId(clientId)
                .alertType(type)
                .severity(severity)
                .description(description)
                .userId(userId)
                .partnerCompanyId(partnerId)
                .incentiveId(incentiveId)
                .status(ComplianceAlertStatus.NEW)
                .build();

        ComplianceAlert saved = complianceAlertRepository.save(alert);
        log.warn("Compliance alert created: id={}, type={}, severity={}, clientId={}",
                saved.getId(), type, severity, clientId);
        return saved;
    }

    @Transactional
    public ComplianceAlert resolveAlert(UUID alertId, UUID resolvedByUserId, String notes) {
        ComplianceAlert alert = complianceAlertRepository.findById(alertId)
                .orElseThrow(() -> new BusinessRuleException(
                        "Compliance alert not found: " + alertId));

        if (alert.getStatus() == ComplianceAlertStatus.RESOLVED
                || alert.getStatus() == ComplianceAlertStatus.DISMISSED) {
            throw new BusinessRuleException(
                    "Alert is already resolved or dismissed: " + alertId);
        }

        alert.setStatus(ComplianceAlertStatus.RESOLVED);
        alert.setResolvedAt(Instant.now());
        alert.setResolvedBy(resolvedByUserId);
        alert.setResolutionNotes(notes);

        ComplianceAlert saved = complianceAlertRepository.save(alert);
        log.info("Compliance alert resolved: id={}, resolvedBy={}", alertId, resolvedByUserId);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<ComplianceAlert> getActiveAlerts(UUID clientId) {
        return complianceAlertRepository.findByClientIdAndStatus(clientId, ComplianceAlertStatus.NEW);
    }

    @Transactional(readOnly = true)
    public Page<ComplianceAlert> getAlerts(UUID clientId, Pageable pageable) {
        return complianceAlertRepository.findByClientId(clientId, pageable);
    }
}
