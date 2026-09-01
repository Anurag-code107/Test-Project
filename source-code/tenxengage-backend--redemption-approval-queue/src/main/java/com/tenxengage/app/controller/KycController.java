package com.tenxengage.app.controller;

import com.tenxengage.app.dto.request.CreateKycRequest;
import com.tenxengage.app.dto.request.RejectKycRequest;
import com.tenxengage.app.dto.request.ResolveAlertRequest;
import com.tenxengage.app.dto.response.ApiResponse;
import com.tenxengage.app.dto.response.ComplianceAlertResponse;
import com.tenxengage.app.dto.response.KycRegionConfigResponse;
import com.tenxengage.app.dto.response.PaginatedResponse;
import com.tenxengage.app.dto.response.PartnerKycResponse;
import com.tenxengage.app.entity.ComplianceAlert;
import com.tenxengage.app.entity.KycRegionConfig;
import com.tenxengage.app.entity.PartnerBeneficialOwner;
import com.tenxengage.app.entity.PartnerKycRecord;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.repository.KycRegionConfigRepository;
import com.tenxengage.app.repository.PartnerBeneficialOwnerRepository;
import com.tenxengage.app.security.RequiresPermission;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.ComplianceAlertService;
import com.tenxengage.app.service.PartnerKycService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/compliance/kyc")
@Tag(name = "KYC & Due Diligence", description = "Partner KYC screening, compliance alerts, risk configuration")
public class KycController {

    private static final Logger log = LoggerFactory.getLogger(KycController.class);

    private final PartnerKycService partnerKycService;
    private final ComplianceAlertService complianceAlertService;
    private final KycRegionConfigRepository kycRegionConfigRepository;
    private final PartnerBeneficialOwnerRepository beneficialOwnerRepository;
    private final TenantValidator tenantValidator;

    public KycController(PartnerKycService partnerKycService,
                          ComplianceAlertService complianceAlertService,
                          KycRegionConfigRepository kycRegionConfigRepository,
                          PartnerBeneficialOwnerRepository beneficialOwnerRepository,
                          TenantValidator tenantValidator) {
        this.partnerKycService = partnerKycService;
        this.complianceAlertService = complianceAlertService;
        this.kycRegionConfigRepository = kycRegionConfigRepository;
        this.beneficialOwnerRepository = beneficialOwnerRepository;
        this.tenantValidator = tenantValidator;
    }

    // -------------------------------------------------------------------------
    // Partner KYC
    // -------------------------------------------------------------------------

    @PostMapping("/partner/{partnerCompanyId}")
    @Operation(summary = "Submit KYC information",
        description = "Submits or updates KYC documentation for a partner company. "
                    + "Creates a new record if none exists, or updates an existing non-approved record. "
                    + "Includes legal entity details and beneficial ownership information.")
    @RequiresPermission("action.compliance.kyc.submit")
    public ResponseEntity<ApiResponse<PartnerKycResponse>> submitKyc(
            @PathVariable UUID partnerCompanyId,
            @Valid @RequestBody CreateKycRequest request) {
        tenantValidator.validatePartnerCompanyAccess(partnerCompanyId);
        UUID clientId = tenantValidator.getCurrentClientId();
        log.info("KYC submission: partnerCompanyId={}, clientId={}", partnerCompanyId, clientId);

        PartnerKycRecord record = partnerKycService.initiateKyc(partnerCompanyId, clientId, request);
        List<PartnerBeneficialOwner> owners = beneficialOwnerRepository.findByKycRecordId(record.getId());

        return ResponseEntity.ok(ApiResponse.success(
                PartnerKycResponse.from(record, owners), "KYC information submitted"));
    }

    @GetMapping("/partner/{partnerCompanyId}")
    @Operation(summary = "Get KYC status",
        description = "Returns the current KYC record and status for a partner company, "
                    + "including beneficial ownership details and approval/rejection information.")
    @RequiresPermission("action.compliance.kyc.view")
    public ResponseEntity<ApiResponse<PartnerKycResponse>> getKycStatus(
            @PathVariable UUID partnerCompanyId) {
        tenantValidator.validatePartnerCompanyAccess(partnerCompanyId);

        PartnerKycRecord record = partnerKycService.getKycRecord(partnerCompanyId)
                .orElseThrow(() -> new BusinessRuleException(
                        "No KYC record found for partner company: " + partnerCompanyId));
        List<PartnerBeneficialOwner> owners = beneficialOwnerRepository.findByKycRecordId(record.getId());

        return ResponseEntity.ok(ApiResponse.success(
                PartnerKycResponse.from(record, owners), "KYC record retrieved"));
    }

    @PostMapping("/partner/{partnerCompanyId}/approve")
    @Operation(summary = "Approve KYC",
        description = "Approves a partner's KYC submission. Sets expiry to 1 year from approval. "
                    + "Only records in IN_PROGRESS status can be approved.")
    @RequiresPermission("action.compliance.kyc.review")
    public ResponseEntity<ApiResponse<PartnerKycResponse>> approveKyc(
            @PathVariable UUID partnerCompanyId) {
        UUID currentUserId = tenantValidator.getCurrentUserId();
        log.info("KYC approval: partnerCompanyId={}, approvedBy={}", partnerCompanyId, currentUserId);

        PartnerKycRecord record = partnerKycService.approveKyc(partnerCompanyId, currentUserId);
        List<PartnerBeneficialOwner> owners = beneficialOwnerRepository.findByKycRecordId(record.getId());

        return ResponseEntity.ok(ApiResponse.success(
                PartnerKycResponse.from(record, owners), "KYC approved"));
    }

    @PostMapping("/partner/{partnerCompanyId}/reject")
    @Operation(summary = "Reject KYC",
        description = "Rejects a partner's KYC submission with a mandatory reason. "
                    + "Only records in IN_PROGRESS status can be rejected.")
    @RequiresPermission("action.compliance.kyc.review")
    public ResponseEntity<ApiResponse<PartnerKycResponse>> rejectKyc(
            @PathVariable UUID partnerCompanyId,
            @Valid @RequestBody RejectKycRequest request) {
        log.info("KYC rejection: partnerCompanyId={}, reason={}", partnerCompanyId, request.reason());

        PartnerKycRecord record = partnerKycService.rejectKyc(partnerCompanyId, request.reason());
        List<PartnerBeneficialOwner> owners = beneficialOwnerRepository.findByKycRecordId(record.getId());

        return ResponseEntity.ok(ApiResponse.success(
                PartnerKycResponse.from(record, owners), "KYC rejected"));
    }

    // -------------------------------------------------------------------------
    // Compliance Alerts
    // -------------------------------------------------------------------------

    @GetMapping("/alerts")
    @Operation(summary = "Get compliance alerts",
        description = "Returns compliance alerts for the current tenant. "
                    + "Supports pagination and filtering by active-only status.")
    @RequiresPermission("action.compliance.kyc.review")
    public ResponseEntity<ApiResponse<PaginatedResponse<ComplianceAlertResponse>>> getAlerts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean activeOnly) {
        UUID clientId = tenantValidator.getCurrentClientId();

        if (activeOnly) {
            List<ComplianceAlert> active = complianceAlertService.getActiveAlerts(clientId);
            List<ComplianceAlertResponse> responses = active.stream()
                    .map(ComplianceAlertResponse::from)
                    .toList();
            PaginatedResponse<ComplianceAlertResponse> paginated = new PaginatedResponse<>(
                    responses, 0, responses.size(), responses.size(), 1, false, false);
            return ResponseEntity.ok(ApiResponse.success(paginated, "Active compliance alerts retrieved"));
        }

        Page<ComplianceAlert> alertPage = complianceAlertService.getAlerts(clientId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        List<ComplianceAlertResponse> responses = alertPage.getContent().stream()
                .map(ComplianceAlertResponse::from)
                .toList();
        PaginatedResponse<ComplianceAlertResponse> paginated = new PaginatedResponse<>(
                responses, alertPage.getNumber(), alertPage.getSize(),
                alertPage.getTotalElements(), alertPage.getTotalPages(),
                alertPage.hasNext(), alertPage.hasPrevious());

        return ResponseEntity.ok(ApiResponse.success(paginated, "Compliance alerts retrieved"));
    }

    @PostMapping("/alerts/{alertId}/resolve")
    @Operation(summary = "Resolve compliance alert",
        description = "Resolves a compliance alert with mandatory resolution notes. "
                    + "Only NEW or INVESTIGATING alerts can be resolved.")
    @RequiresPermission("action.compliance.kyc.review")
    public ResponseEntity<ApiResponse<ComplianceAlertResponse>> resolveAlert(
            @PathVariable UUID alertId,
            @Valid @RequestBody ResolveAlertRequest request) {
        UUID currentUserId = tenantValidator.getCurrentUserId();
        log.info("Resolving compliance alert: alertId={}, resolvedBy={}", alertId, currentUserId);

        ComplianceAlert resolved = complianceAlertService.resolveAlert(
                alertId, currentUserId, request.notes());

        return ResponseEntity.ok(ApiResponse.success(
                ComplianceAlertResponse.from(resolved), "Compliance alert resolved"));
    }

    // -------------------------------------------------------------------------
    // Risk Configuration
    // -------------------------------------------------------------------------

    @GetMapping("/risk-config")
    @Operation(summary = "Get KYC region configuration",
        description = "Returns the KYC requirements by region. Shows which regions require "
                    + "Tier 1 (basic) and Tier 2 (enhanced) KYC due diligence.")
    @RequiresPermission("action.compliance.kyc.view")
    public ResponseEntity<ApiResponse<List<KycRegionConfigResponse>>> getRiskConfig() {
        List<KycRegionConfig> configs = kycRegionConfigRepository.findAll();
        List<KycRegionConfigResponse> responses = configs.stream()
                .map(KycRegionConfigResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(responses, "KYC region configuration retrieved"));
    }
}
