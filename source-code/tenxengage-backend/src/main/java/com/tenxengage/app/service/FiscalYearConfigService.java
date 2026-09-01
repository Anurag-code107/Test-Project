package com.tenxengage.app.service;

import com.tenxengage.app.dto.request.SaveFiscalYearConfigRequest;
import com.tenxengage.app.dto.response.FiscalYearConfigResponse;
import com.tenxengage.app.dto.response.FiscalYearLabelResponse;
import com.tenxengage.app.entity.FiscalYearConfig;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.FiscalYearConfigRepository;
import com.tenxengage.app.security.TenantValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class FiscalYearConfigService {

    private static final Logger log = LoggerFactory.getLogger(FiscalYearConfigService.class);

    private final FiscalYearConfigRepository repository;
    private final TenantValidator tenantValidator;

    public FiscalYearConfigService(FiscalYearConfigRepository repository,
                                   TenantValidator tenantValidator) {
        this.repository = repository;
        this.tenantValidator = tenantValidator;
    }

    @Transactional(readOnly = true)
    public List<FiscalYearConfigResponse> listConfigs() {
        UUID clientId = tenantValidator.getCurrentClientId();
        return repository.findByClientIdOrderByStartDateAsc(clientId).stream()
            .map(FiscalYearConfigResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public FiscalYearConfigResponse getConfig(UUID id) {
        FiscalYearConfig config = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("FiscalYearConfig", "id", id));
        tenantValidator.validateClientAccess(config.getClientId());
        return FiscalYearConfigResponse.from(config);
    }

    @Transactional(readOnly = true)
    public FiscalYearConfigResponse getConfigByLabel(String label) {
        UUID clientId = tenantValidator.getCurrentClientId();
        FiscalYearConfig config = repository.findByClientIdAndLabel(clientId, label)
            .orElseThrow(() -> new ResourceNotFoundException("FiscalYearConfig", "label", label));
        return FiscalYearConfigResponse.from(config);
    }

    @Transactional(readOnly = true)
    public FiscalYearConfigResponse getCurrentConfig() {
        UUID clientId = tenantValidator.getCurrentClientId();
        LocalDate today = LocalDate.now();
        List<FiscalYearConfig> configs = repository.findByClientIdOrderByStartDateAsc(clientId);
        return configs.stream()
            .filter(c -> !today.isBefore(c.getStartDate()) && !today.isAfter(c.getEndDate()))
            .findFirst()
            .map(FiscalYearConfigResponse::from)
            .orElseThrow(() -> new ResourceNotFoundException(
                "No fiscal year config found containing today's date for client " + clientId));
    }

    @Transactional(readOnly = true)
    public List<FiscalYearLabelResponse> listLabels() {
        UUID clientId = tenantValidator.getCurrentClientId();
        return repository.findByClientIdOrderByStartDateAsc(clientId).stream()
            .map(FiscalYearLabelResponse::from)
            .toList();
    }

    @Transactional
    public FiscalYearConfigResponse createConfig(SaveFiscalYearConfigRequest request) {
        UUID clientId = tenantValidator.getCurrentClientId();

        if (repository.existsByClientIdAndLabel(clientId, request.label())) {
            throw new IllegalArgumentException(
                "A fiscal year config with label '" + request.label() + "' already exists");
        }

        validateQuarterContiguity(request);

        FiscalYearConfig config = FiscalYearConfig.builder()
            .clientId(clientId)
            .label(request.label())
            .startDate(request.startDate())
            .endDate(request.endDate())
            .quarterMethod(request.quarterMethod())
            .quarterSize(request.quarterSize())
            .q1StartDate(request.q1StartDate())
            .q1EndDate(request.q1EndDate())
            .q2StartDate(request.q2StartDate())
            .q2EndDate(request.q2EndDate())
            .q3StartDate(request.q3StartDate())
            .q3EndDate(request.q3EndDate())
            .q4StartDate(request.q4StartDate())
            .q4EndDate(request.q4EndDate())
            .build();

        config = repository.save(config);
        log.info("Created fiscal year config '{}' for client {}", config.getLabel(), clientId);
        return FiscalYearConfigResponse.from(config);
    }

    @Transactional
    public FiscalYearConfigResponse updateConfig(UUID id, SaveFiscalYearConfigRequest request) {
        FiscalYearConfig config = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("FiscalYearConfig", "id", id));
        tenantValidator.validateClientAccess(config.getClientId());

        // Check label uniqueness if changed
        if (!config.getLabel().equals(request.label())
                && repository.existsByClientIdAndLabel(config.getClientId(), request.label())) {
            throw new IllegalArgumentException(
                "A fiscal year config with label '" + request.label() + "' already exists");
        }

        validateQuarterContiguity(request);

        config.setLabel(request.label());
        config.setStartDate(request.startDate());
        config.setEndDate(request.endDate());
        config.setQuarterMethod(request.quarterMethod());
        config.setQuarterSize(request.quarterSize());
        config.setQ1StartDate(request.q1StartDate());
        config.setQ1EndDate(request.q1EndDate());
        config.setQ2StartDate(request.q2StartDate());
        config.setQ2EndDate(request.q2EndDate());
        config.setQ3StartDate(request.q3StartDate());
        config.setQ3EndDate(request.q3EndDate());
        config.setQ4StartDate(request.q4StartDate());
        config.setQ4EndDate(request.q4EndDate());

        config = repository.save(config);
        log.info("Updated fiscal year config '{}' (id={}) for client {}", config.getLabel(), id, config.getClientId());
        return FiscalYearConfigResponse.from(config);
    }

    @Transactional
    public void deleteConfig(UUID id) {
        FiscalYearConfig config = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("FiscalYearConfig", "id", id));
        tenantValidator.validateClientAccess(config.getClientId());

        repository.delete(config);
        log.info("Deleted fiscal year config '{}' (id={}) for client {}", config.getLabel(), id, config.getClientId());
    }

    @Transactional
    public FiscalYearConfig generateNextConfig(UUID clientId) {
        List<FiscalYearConfig> configs = repository.findByClientIdOrderByStartDateAsc(clientId);
        if (configs.isEmpty()) {
            return null;
        }

        FiscalYearConfig latest = configs.get(configs.size() - 1);

        LocalDate newStartDate;
        LocalDate newEndDate;
        LocalDate newQ1Start;
        LocalDate newQ1End;
        LocalDate newQ2Start;
        LocalDate newQ2End;
        LocalDate newQ3Start;
        LocalDate newQ3End;
        LocalDate newQ4Start;
        LocalDate newQ4End;

        switch (latest.getQuarterMethod()) {
            case MONTHS -> {
                int months = latest.getQuarterSize() * 4;
                newStartDate = latest.getStartDate().plusMonths(months);
                newEndDate = latest.getEndDate().plusMonths(months);
                newQ1Start = latest.getQ1StartDate().plusMonths(months);
                newQ1End = latest.getQ1EndDate().plusMonths(months);
                newQ2Start = latest.getQ2StartDate().plusMonths(months);
                newQ2End = latest.getQ2EndDate().plusMonths(months);
                newQ3Start = latest.getQ3StartDate().plusMonths(months);
                newQ3End = latest.getQ3EndDate().plusMonths(months);
                newQ4Start = latest.getQ4StartDate().plusMonths(months);
                newQ4End = latest.getQ4EndDate().plusMonths(months);
            }
            case WEEKS -> {
                int weeks = latest.getQuarterSize() * 4;
                newStartDate = latest.getStartDate().plusWeeks(weeks);
                newEndDate = latest.getEndDate().plusWeeks(weeks);
                newQ1Start = latest.getQ1StartDate().plusWeeks(weeks);
                newQ1End = latest.getQ1EndDate().plusWeeks(weeks);
                newQ2Start = latest.getQ2StartDate().plusWeeks(weeks);
                newQ2End = latest.getQ2EndDate().plusWeeks(weeks);
                newQ3Start = latest.getQ3StartDate().plusWeeks(weeks);
                newQ3End = latest.getQ3EndDate().plusWeeks(weeks);
                newQ4Start = latest.getQ4StartDate().plusWeeks(weeks);
                newQ4End = latest.getQ4EndDate().plusWeeks(weeks);
            }
            case DAYS -> {
                int days = latest.getQuarterSize() * 4;
                newStartDate = latest.getStartDate().plusDays(days);
                newEndDate = latest.getEndDate().plusDays(days);
                newQ1Start = latest.getQ1StartDate().plusDays(days);
                newQ1End = latest.getQ1EndDate().plusDays(days);
                newQ2Start = latest.getQ2StartDate().plusDays(days);
                newQ2End = latest.getQ2EndDate().plusDays(days);
                newQ3Start = latest.getQ3StartDate().plusDays(days);
                newQ3End = latest.getQ3EndDate().plusDays(days);
                newQ4Start = latest.getQ4StartDate().plusDays(days);
                newQ4End = latest.getQ4EndDate().plusDays(days);
            }
            case CUSTOM -> {
                long offsetDays = ChronoUnit.DAYS.between(latest.getStartDate(), latest.getEndDate()) + 1;
                newStartDate = latest.getStartDate().plusDays(offsetDays);
                newEndDate = latest.getEndDate().plusDays(offsetDays);
                newQ1Start = latest.getQ1StartDate().plusDays(offsetDays);
                newQ1End = latest.getQ1EndDate().plusDays(offsetDays);
                newQ2Start = latest.getQ2StartDate().plusDays(offsetDays);
                newQ2End = latest.getQ2EndDate().plusDays(offsetDays);
                newQ3Start = latest.getQ3StartDate().plusDays(offsetDays);
                newQ3End = latest.getQ3EndDate().plusDays(offsetDays);
                newQ4Start = latest.getQ4StartDate().plusDays(offsetDays);
                newQ4End = latest.getQ4EndDate().plusDays(offsetDays);
            }
            default -> throw new IllegalStateException("Unsupported quarter method: " + latest.getQuarterMethod());
        }

        String label = "FY" + newStartDate.getYear();
        if (repository.existsByClientIdAndLabel(clientId, label)) {
            return null;
        }

        FiscalYearConfig next = FiscalYearConfig.builder()
            .clientId(clientId)
            .label(label)
            .startDate(newStartDate)
            .endDate(newEndDate)
            .quarterMethod(latest.getQuarterMethod())
            .quarterSize(latest.getQuarterSize())
            .q1StartDate(newQ1Start)
            .q1EndDate(newQ1End)
            .q2StartDate(newQ2Start)
            .q2EndDate(newQ2End)
            .q3StartDate(newQ3Start)
            .q3EndDate(newQ3End)
            .q4StartDate(newQ4Start)
            .q4EndDate(newQ4End)
            .build();

        next = repository.save(next);
        log.info("Auto-generated fiscal year config '{}' for client {}", label, clientId);
        return next;
    }

    private void validateQuarterContiguity(SaveFiscalYearConfigRequest request) {
        // Q1 start must match FY start
        if (!request.q1StartDate().equals(request.startDate())) {
            throw new IllegalArgumentException("Q1 start date must match fiscal year start date");
        }
        // Q4 end must match FY end
        if (!request.q4EndDate().equals(request.endDate())) {
            throw new IllegalArgumentException("Q4 end date must match fiscal year end date");
        }
        // Each quarter end must be the day before the next quarter start
        if (!request.q1EndDate().plusDays(1).equals(request.q2StartDate())) {
            throw new IllegalArgumentException("Q1 end date must be the day before Q2 start date");
        }
        if (!request.q2EndDate().plusDays(1).equals(request.q3StartDate())) {
            throw new IllegalArgumentException("Q2 end date must be the day before Q3 start date");
        }
        if (!request.q3EndDate().plusDays(1).equals(request.q4StartDate())) {
            throw new IllegalArgumentException("Q3 end date must be the day before Q4 start date");
        }
    }
}
