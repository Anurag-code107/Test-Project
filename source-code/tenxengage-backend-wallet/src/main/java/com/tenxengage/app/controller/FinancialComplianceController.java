package com.tenxengage.app.controller;

import com.tenxengage.app.dto.request.AddCaseUpdateRequest;
import com.tenxengage.app.dto.request.CreateWhistleblowerReportRequest;
import com.tenxengage.app.dto.request.ResolveWhistleblowerRequest;
import com.tenxengage.app.dto.response.AnnualRewardSummaryResponse;
import com.tenxengage.app.dto.response.ApiResponse;
import com.tenxengage.app.dto.response.EmployerBikReportResponse;
import com.tenxengage.app.dto.response.WhistleblowerReportResponse;
import com.tenxengage.app.dto.response.WhistleblowerStatusResponse;
import com.tenxengage.app.entity.WhistleblowerCaseUpdate;
import com.tenxengage.app.entity.WhistleblowerReport;
import com.tenxengage.app.security.RequiresPermission;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.TaxReportingService;
import com.tenxengage.app.service.WhistleblowerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/compliance/financial")
@Tag(name = "Financial Compliance",
     description = "BIK tax tracking, AML monitoring, and whistleblower channel")
public class FinancialComplianceController {

    private static final Logger log = LoggerFactory.getLogger(FinancialComplianceController.class);

    private final WhistleblowerService whistleblowerService;
    private final TaxReportingService taxReportingService;
    private final TenantValidator tenantValidator;

    public FinancialComplianceController(WhistleblowerService whistleblowerService,
                                          TaxReportingService taxReportingService,
                                          TenantValidator tenantValidator) {
        this.whistleblowerService = whistleblowerService;
        this.taxReportingService = taxReportingService;
        this.tenantValidator = tenantValidator;
    }

    // -------------------------------------------------------------------------
    // Whistleblower Channel - Public Endpoints
    // -------------------------------------------------------------------------

    @PostMapping("/whistleblower/report")
    @Operation(summary = "Submit a whistleblower report",
        description = "Public endpoint. Allows anonymous or identified reporting of compliance "
                    + "concerns. Returns a tracking number for status checks. "
                    + "EU Whistleblower Protection Directive 2019/1937 compliant.")
    public ResponseEntity<ApiResponse<WhistleblowerStatusResponse>> submitReport(
            @Valid @RequestBody CreateWhistleblowerReportRequest request) {
        log.info("Whistleblower report submitted: type={}, anonymous={}",
                request.reportType(), request.anonymous());

        WhistleblowerReport report = whistleblowerService.submitReport(request);
        List<WhistleblowerCaseUpdate> updates = List.of();
        WhistleblowerStatusResponse response = WhistleblowerStatusResponse.from(report, updates);

        return ResponseEntity.ok(ApiResponse.success(response,
                "Report submitted. Your tracking number is: " + report.getTrackingNumber()));
    }

    @GetMapping("/whistleblower/status/{trackingNumber}")
    @Operation(summary = "Check whistleblower report status",
        description = "Public endpoint. Allows reporters to check the status of their report "
                    + "using the tracking number received at submission time.")
    public ResponseEntity<ApiResponse<WhistleblowerStatusResponse>> checkStatus(
            @PathVariable String trackingNumber) {
        WhistleblowerReport report = whistleblowerService
                .getReportByTrackingNumber(trackingNumber);
        List<WhistleblowerCaseUpdate> updates = whistleblowerService
                .getCaseUpdates(report.getId());
        WhistleblowerStatusResponse response = WhistleblowerStatusResponse.from(report, updates);

        return ResponseEntity.ok(ApiResponse.success(response, "Report status retrieved"));
    }

    // -------------------------------------------------------------------------
    // Whistleblower Channel - Admin Endpoints
    // -------------------------------------------------------------------------

    @GetMapping("/whistleblower/reports")
    @Operation(summary = "List active whistleblower reports",
        description = "TENX_ADMIN only. Returns all active (non-resolved, non-dismissed) "
                    + "whistleblower reports across all tenants.")
    @RequiresPermission("action.tenx.compliance.manage")
    public ResponseEntity<ApiResponse<List<WhistleblowerReportResponse>>> getActiveReports() {
        List<WhistleblowerReport> reports = whistleblowerService.getActiveReports();
        List<WhistleblowerReportResponse> response = reports.stream()
                .map(WhistleblowerReportResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(response,
                "Active whistleblower reports retrieved"));
    }

    @PostMapping("/whistleblower/reports/{id}/acknowledge")
    @Operation(summary = "Acknowledge a whistleblower report",
        description = "TENX_ADMIN only. Acknowledges receipt of the report and sets a "
                    + "resolution deadline of 3 months per EU Directive 2019/1937.")
    @RequiresPermission("action.tenx.compliance.manage")
    public ResponseEntity<ApiResponse<WhistleblowerReportResponse>> acknowledgeReport(
            @PathVariable UUID id) {
        log.info("Acknowledging whistleblower report: id={}", id);
        WhistleblowerReport report = whistleblowerService.acknowledgeReport(id);
        return ResponseEntity.ok(ApiResponse.success(
                WhistleblowerReportResponse.from(report), "Report acknowledged"));
    }

    @PostMapping("/whistleblower/reports/{id}/resolve")
    @Operation(summary = "Resolve a whistleblower report",
        description = "TENX_ADMIN only. Marks the report as resolved with resolution notes.")
    @RequiresPermission("action.tenx.compliance.manage")
    public ResponseEntity<ApiResponse<WhistleblowerReportResponse>> resolveReport(
            @PathVariable UUID id,
            @Valid @RequestBody ResolveWhistleblowerRequest request) {
        UUID currentUserId = tenantValidator.getCurrentUserId();
        log.info("Resolving whistleblower report: id={}, resolvedBy={}", id, currentUserId);

        WhistleblowerReport report = whistleblowerService
                .resolveReport(id, currentUserId, request.notes());
        return ResponseEntity.ok(ApiResponse.success(
                WhistleblowerReportResponse.from(report), "Report resolved"));
    }

    @PostMapping("/whistleblower/reports/{id}/update")
    @Operation(summary = "Add a case update to a whistleblower report",
        description = "TENX_ADMIN only. Adds an investigation update to the report. "
                    + "Automatically transitions report to UNDER_INVESTIGATION if ACKNOWLEDGED.")
    @RequiresPermission("action.tenx.compliance.manage")
    public ResponseEntity<ApiResponse<Void>> addCaseUpdate(
            @PathVariable UUID id,
            @Valid @RequestBody AddCaseUpdateRequest request) {
        UUID currentUserId = tenantValidator.getCurrentUserId();
        log.info("Adding case update: reportId={}, updatedBy={}", id, currentUserId);

        whistleblowerService.addCaseUpdate(id, request.updateText(), currentUserId);
        return ResponseEntity.ok(ApiResponse.success(null, "Case update added"));
    }

    // -------------------------------------------------------------------------
    // Tax Reporting - BIK
    // -------------------------------------------------------------------------

    @GetMapping("/tax/annual-summary")
    @Operation(summary = "Get annual reward summary for BIK tax reporting",
        description = "CLIENT_ADMIN or TENX_ADMIN. Returns per-user, per-currency reward "
                    + "totals for the specified year. Used for Benefit-in-Kind tax reporting "
                    + "across EU jurisdictions.")
    @RequiresPermission("action.compliance.tax.view")
    public ResponseEntity<ApiResponse<List<AnnualRewardSummaryResponse>>> getAnnualSummary(
            @RequestParam int year) {
        UUID clientId = tenantValidator.getCurrentClientId();
        List<AnnualRewardSummaryResponse> summaries =
                taxReportingService.getUserAnnualRewardSummary(clientId, year);
        return ResponseEntity.ok(ApiResponse.success(summaries,
                "Annual reward summary retrieved"));
    }

    @GetMapping("/tax/employer-report/{partnerCompanyId}")
    @Operation(summary = "Get employer BIK report for a partner company",
        description = "CLIENT_ADMIN, PARTNER_ADMIN, or TENX_ADMIN. Returns aggregated "
                    + "reward data per employee with USD equivalents for tax filing.")
    @RequiresPermission("action.compliance.tax.view")
    public ResponseEntity<ApiResponse<EmployerBikReportResponse>> getEmployerReport(
            @PathVariable UUID partnerCompanyId,
            @RequestParam int year) {
        tenantValidator.validatePartnerCompanyAccess(partnerCompanyId);
        EmployerBikReportResponse report =
                taxReportingService.getEmployerBikReport(partnerCompanyId, year);
        return ResponseEntity.ok(ApiResponse.success(report, "Employer BIK report retrieved"));
    }

    @GetMapping("/tax/export")
    @Operation(summary = "Export tax report data",
        description = "CLIENT_ADMIN or TENX_ADMIN. Exports structured tax report data "
                    + "suitable for CSV or JSON conversion. Includes USD equivalents "
                    + "based on configured currency conversion rates.")
    @RequiresPermission("action.compliance.tax.export")
    public ResponseEntity<ApiResponse<Map<String, Object>>> exportTaxReport(
            @RequestParam int year,
            @RequestParam(defaultValue = "json") String format) {
        UUID clientId = tenantValidator.getCurrentClientId();
        Map<String, Object> exportData = taxReportingService
                .exportTaxReport(clientId, year, format);
        return ResponseEntity.ok(ApiResponse.success(exportData, "Tax report exported"));
    }
}
