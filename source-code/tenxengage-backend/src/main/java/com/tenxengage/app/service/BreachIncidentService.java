package com.tenxengage.app.service;

import com.tenxengage.app.entity.BreachIncident;
import com.tenxengage.app.entity.enums.BreachSeverity;
import com.tenxengage.app.entity.enums.BreachStatus;
import com.tenxengage.app.repository.BreachIncidentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class BreachIncidentService {

    private static final Logger log = LoggerFactory.getLogger(BreachIncidentService.class);

    private final BreachIncidentRepository breachIncidentRepository;

    public BreachIncidentService(BreachIncidentRepository breachIncidentRepository) {
        this.breachIncidentRepository = breachIncidentRepository;
    }

    @Transactional(readOnly = true)
    public List<BreachIncident> getActiveIncidents() {
        return breachIncidentRepository.findByStatusNot(BreachStatus.CLOSED);
    }

    @Transactional
    public BreachIncident create(String description, String severity, String dataAffected,
                                 Instant detectedAt, UUID createdBy) {
        BreachIncident incident = BreachIncident.builder()
                .description(description)
                .severity(BreachSeverity.valueOf(severity))
                .dataAffected(dataAffected)
                .detectedAt(detectedAt)
                .status(BreachStatus.DETECTED)
                .createdBy(createdBy)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        BreachIncident saved = breachIncidentRepository.save(incident);
        log.warn("Breach incident created: id={}, severity={}, description={}",
                saved.getId(), saved.getSeverity(), saved.getDescription());
        return saved;
    }
}
