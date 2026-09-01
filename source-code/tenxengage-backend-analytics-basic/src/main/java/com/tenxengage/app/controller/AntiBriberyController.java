package com.tenxengage.app.controller;

import com.tenxengage.app.dto.request.AcknowledgeProgramRequest;
import com.tenxengage.app.dto.request.UpdateComplianceValueCapRequest;
import com.tenxengage.app.dto.request.UpdateGovernmentSegmentsRequest;
import com.tenxengage.app.dto.response.AcknowledgmentResponse;
import com.tenxengage.app.dto.response.ApiResponse;
import com.tenxengage.app.dto.response.ComplianceValueCapResponse;
import com.tenxengage.app.dto.response.GovernmentSegmentResponse;
import com.tenxengage.app.entity.ComplianceValueCap;
import com.tenxengage.app.entity.GovernmentSegmentConfig;
import com.tenxengage.app.entity.PartnerProgramAcknowledgment;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.repository.ComplianceValueCapRepository;
import com.tenxengage.app.repository.GovernmentSegmentConfigRepository;
import com.tenxengage.app.security.RequiresPermission;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.ComplianceCapValidator;
import com.tenxengage.app.service.GovernmentDealService;
import com.tenxengage.app.service.PartnerAcknowledgmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/compliance/anti-bribery")
@Tag(name = "Anti-Bribery", description = "Partner acknowledgments, government deal identification, country value caps")
public class AntiBriberyController {

    private static final Logger log = LoggerFactory.getLogger(AntiBriberyController.class);

    private final PartnerAcknowledgmentService partnerAcknowledgmentService;
    private final ComplianceCapValidator complianceCapValidator;
    private final ComplianceValueCapRepository complianceValueCapRepository;
    private final GovernmentSegmentConfigRepository governmentSegmentConfigRepository;
    private final GovernmentDealService governmentDealService;
    private final TenantValidator tenantValidator;

    public AntiBriberyController(PartnerAcknowledgmentService partnerAcknowledgmentService,
                                 ComplianceCapValidator complianceCapValidator,
                                 ComplianceValueCapRepository complianceValueCapRepository,
                                 GovernmentSegmentConfigRepository governmentSegmentConfigRepository,
                                 GovernmentDealService governmentDealService,
                                 TenantValidator tenantValidator) {
        this.partnerAcknowledgmentService = partnerAcknowledgmentService;
        this.complianceCapValidator = complianceCapValidator;
        this.complianceValueCapRepository = complianceValueCapRepository;
        this.governmentSegmentConfigRepository = governmentSegmentConfigRepository;
        this.governmentDealService = governmentDealService;
        this.tenantValidator = tenantValidator;
    }

    // -------------------------------------------------------------------------
    // Partner Program Acknowledgments
    // -------------------------------------------------------------------------

    @PostMapping("/acknowledgments")
    @Operation(summary = "Acknowledge anti-bribery program",
        description = "Records that a partner company has acknowledged the anti-bribery policy "
                    + "for a specific incentive program. Required before partner can participate.")
    @RequiresPermission("action.compliance.anti_bribery.acknowledge")
    public ResponseEntity<ApiResponse<AcknowledgmentResponse>> acknowledgeProgram(
            @Valid @RequestBody AcknowledgeProgramRequest request) {
        UUID clientId = tenantValidator.getCurrentClientId();
        UUID userId = tenantValidator.getCurrentUserId();
        tenantValidator.validatePartnerCompanyAccess(request.partnerCompanyId());

        log.info("Partner acknowledgment: partnerCompanyId={}, incentiveId={}, userId={}",
                request.partnerCompanyId(), request.incentiveId(), userId);

        PartnerProgramAcknowledgment ack = partnerAcknowledgmentService.acknowledgeProgram(
                request.partnerCompanyId(), request.incentiveId(), userId, clientId);
        return ResponseEntity.ok(ApiResponse.success(
                AcknowledgmentResponse.from(ack), "Program acknowledged"));
    }

    @GetMapping("/acknowledgments/incentive/{incentiveId}")
    @Operation(summary = "Get acknowledgments for incentive",
        description = "Returns all partner acknowledgments for a specific incentive program.")
    @RequiresPermission("action.compliance.anti_bribery.view")
    public ResponseEntity<ApiResponse<List<AcknowledgmentResponse>>> getAcknowledgmentsForIncentive(
            @PathVariable UUID incentiveId) {
        List<PartnerProgramAcknowledgment> acks =
                partnerAcknowledgmentService.getAcknowledgmentsForIncentive(incentiveId);
        List<AcknowledgmentResponse> response = acks.stream()
                .map(AcknowledgmentResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(response, "Acknowledgments retrieved"));
    }

    @GetMapping("/acknowledgments/partner/{partnerCompanyId}")
    @Operation(summary = "Get acknowledgments for partner",
        description = "Returns all program acknowledgments for a specific partner company.")
    @RequiresPermission("action.compliance.anti_bribery.view")
    public ResponseEntity<ApiResponse<List<AcknowledgmentResponse>>> getAcknowledgmentsForPartner(
            @PathVariable UUID partnerCompanyId) {
        tenantValidator.validatePartnerCompanyAccess(partnerCompanyId);

        List<PartnerProgramAcknowledgment> acks =
                partnerAcknowledgmentService.getAcknowledgmentsForPartner(partnerCompanyId);
        List<AcknowledgmentResponse> response = acks.stream()
                .map(AcknowledgmentResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(response, "Acknowledgments retrieved"));
    }

    // -------------------------------------------------------------------------
    // Country-Specific Compliance Value Caps
    // -------------------------------------------------------------------------

    @GetMapping("/value-caps")
    @Operation(summary = "Get effective value caps",
        description = "Returns the effective compliance value caps for the current tenant. "
                    + "Client-specific overrides take precedence over system defaults.")
    @RequiresPermission("action.compliance.anti_bribery.view")
    public ResponseEntity<ApiResponse<List<ComplianceValueCapResponse>>> getValueCaps(
            @RequestParam(required = false) String countryCode) {
        UUID clientId = tenantValidator.getCurrentClientId();

        if (countryCode != null && !countryCode.isBlank()) {
            return complianceCapValidator.getEffectiveCap(countryCode, clientId)
                    .map(cap -> ResponseEntity.ok(ApiResponse.success(
                            List.of(ComplianceValueCapResponse.from(cap)), "Value cap retrieved")))
                    .orElseGet(() -> ResponseEntity.ok(
                            ApiResponse.success(List.of(), "No cap configured for country: " + countryCode)));
        }

        // Return all system defaults (effective caps for the tenant)
        List<ComplianceValueCap> defaults = complianceValueCapRepository.findByClientIdIsNull();
        List<ComplianceValueCapResponse> response = defaults.stream()
                .map(defaultCap -> {
                    ComplianceValueCap effective = complianceCapValidator
                            .getEffectiveCap(defaultCap.getCountryCode(), clientId)
                            .orElse(defaultCap);
                    return ComplianceValueCapResponse.from(effective);
                })
                .toList();
        return ResponseEntity.ok(ApiResponse.success(response, "Value caps retrieved"));
    }

    @GetMapping("/value-caps/defaults")
    @Operation(summary = "Get system default value caps",
        description = "Returns the system-wide default compliance value caps. TENX_ADMIN only.")
    @RequiresPermission("action.tenx.compliance.manage")
    public ResponseEntity<ApiResponse<List<ComplianceValueCapResponse>>> getDefaultValueCaps() {
        List<ComplianceValueCap> defaults = complianceValueCapRepository.findByClientIdIsNull();
        List<ComplianceValueCapResponse> response = defaults.stream()
                .map(ComplianceValueCapResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(response, "System default value caps retrieved"));
    }

    @PutMapping("/value-caps/{countryCode}")
    @Operation(summary = "Update value cap for country",
        description = "Updates or creates a client-specific compliance value cap for a country. "
                    + "CLIENT_ADMIN can only make caps stricter (lower) than the system default.")
    @RequiresPermission("action.compliance.anti_bribery.manage")
    public ResponseEntity<ApiResponse<ComplianceValueCapResponse>> updateValueCap(
            @PathVariable String countryCode,
            @Valid @RequestBody UpdateComplianceValueCapRequest request) {
        UUID clientId = tenantValidator.getCurrentClientId();

        // Enforce that client admins can only make caps stricter
        complianceValueCapRepository.findByCountryCodeAndClientIdIsNull(countryCode)
                .ifPresent(systemDefault -> {
                    if (request.annualCapAmount().compareTo(systemDefault.getAnnualCapAmount()) > 0) {
                        throw new BusinessRuleException(String.format(
                                "Client cap (%s) cannot exceed system default (%s) for country %s",
                                request.annualCapAmount(), systemDefault.getAnnualCapAmount(), countryCode));
                    }
                    if (request.enhancedApprovalThreshold()
                            .compareTo(systemDefault.getEnhancedApprovalThreshold()) > 0) {
                        throw new BusinessRuleException(String.format(
                                "Client threshold (%s) cannot exceed system default (%s) for country %s",
                                request.enhancedApprovalThreshold(),
                                systemDefault.getEnhancedApprovalThreshold(), countryCode));
                    }
                });

        ComplianceValueCap cap = complianceValueCapRepository
                .findByCountryCodeAndClientId(countryCode, clientId)
                .orElseGet(() -> {
                    ComplianceValueCap newCap = new ComplianceValueCap();
                    newCap.setCountryCode(countryCode);
                    newCap.setClientId(clientId);
                    newCap.setCreatedAt(Instant.now());
                    // Copy currency from system default
                    complianceValueCapRepository.findByCountryCodeAndClientIdIsNull(countryCode)
                            .ifPresent(d -> newCap.setAnnualCapCurrency(d.getAnnualCapCurrency()));
                    return newCap;
                });

        cap.setAnnualCapAmount(request.annualCapAmount());
        cap.setEnhancedApprovalThreshold(request.enhancedApprovalThreshold());
        cap.setUpdatedAt(Instant.now());

        ComplianceValueCap saved = complianceValueCapRepository.save(cap);
        log.info("Value cap updated: countryCode={}, clientId={}, cap={}, threshold={}",
                countryCode, clientId, request.annualCapAmount(), request.enhancedApprovalThreshold());

        return ResponseEntity.ok(ApiResponse.success(
                ComplianceValueCapResponse.from(saved), "Value cap updated"));
    }

    // -------------------------------------------------------------------------
    // Government Segment Configuration
    // -------------------------------------------------------------------------

    @GetMapping("/government-segments")
    @Operation(summary = "Get government segment configuration",
        description = "Returns the segment values classified as government for the current tenant.")
    @RequiresPermission("action.compliance.anti_bribery.view")
    public ResponseEntity<ApiResponse<List<GovernmentSegmentResponse>>> getGovernmentSegments() {
        UUID clientId = tenantValidator.getCurrentClientId();
        List<GovernmentSegmentConfig> configs = governmentSegmentConfigRepository.findByClientId(clientId);
        List<GovernmentSegmentResponse> response = configs.stream()
                .map(GovernmentSegmentResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(response, "Government segments retrieved"));
    }

    @PutMapping("/government-segments")
    @Operation(summary = "Update government segment configuration",
        description = "Replaces the government segment configuration for the current tenant. "
                    + "Provide the full list of segment values that should be classified as government.")
    @RequiresPermission("action.compliance.anti_bribery.manage")
    public ResponseEntity<ApiResponse<List<GovernmentSegmentResponse>>> updateGovernmentSegments(
            @Valid @RequestBody UpdateGovernmentSegmentsRequest request) {
        UUID clientId = tenantValidator.getCurrentClientId();
        log.info("Updating government segments: clientId={}, segments={}", clientId, request.segmentValues());

        List<GovernmentSegmentConfig> updated =
                governmentDealService.updateGovernmentSegments(clientId, request.segmentValues());
        List<GovernmentSegmentResponse> response = updated.stream()
                .map(GovernmentSegmentResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(response, "Government segments updated"));
    }
}
