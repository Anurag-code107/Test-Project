package com.tenxengage.app.controller;

import com.tenxengage.app.dto.request.CreateBreachIncidentRequest;
import com.tenxengage.app.dto.request.CreateSubProcessorRequest;
import com.tenxengage.app.dto.request.UpdateRetentionPolicyRequest;
import com.tenxengage.app.dto.response.ApiResponse;
import com.tenxengage.app.dto.response.BreachIncidentResponse;
import com.tenxengage.app.dto.response.RetentionBoundsResponse;
import com.tenxengage.app.dto.response.RetentionPolicyResponse;
import com.tenxengage.app.dto.response.SubProcessorResponse;
import com.tenxengage.app.entity.BreachIncident;
import com.tenxengage.app.entity.RetentionPolicy;
import com.tenxengage.app.entity.SubProcessor;
import com.tenxengage.app.security.RequiresPermission;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.BreachIncidentService;
import com.tenxengage.app.service.DataRetentionService;
import com.tenxengage.app.service.SubProcessorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/compliance")
@Tag(name = "Compliance", description = "Data governance: retention policies, sub-processors, breach incidents")
public class ComplianceController {

    private static final Logger log = LoggerFactory.getLogger(ComplianceController.class);

    private final DataRetentionService dataRetentionService;
    private final SubProcessorService subProcessorService;
    private final BreachIncidentService breachIncidentService;
    private final TenantValidator tenantValidator;

    public ComplianceController(DataRetentionService dataRetentionService,
                                SubProcessorService subProcessorService,
                                BreachIncidentService breachIncidentService,
                                TenantValidator tenantValidator) {
        this.dataRetentionService = dataRetentionService;
        this.subProcessorService = subProcessorService;
        this.breachIncidentService = breachIncidentService;
        this.tenantValidator = tenantValidator;
    }

    // -------------------------------------------------------------------------
    // Retention Policies
    // -------------------------------------------------------------------------

    @GetMapping("/retention-policies")
    @Operation(summary = "Get effective retention policies",
        description = "Returns the effective retention policies for the current tenant. "
                    + "Client-specific overrides take precedence over system defaults.")
    @RequiresPermission("action.compliance.retention.view")
    public ResponseEntity<ApiResponse<List<RetentionPolicyResponse>>> getRetentionPolicies() {
        UUID clientId = tenantValidator.getCurrentClientId();
        List<RetentionPolicy> policies = dataRetentionService.getRetentionPolicies(clientId);
        List<RetentionPolicyResponse> response = policies.stream()
                .map(RetentionPolicyResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(response, "Retention policies retrieved"));
    }

    @PutMapping("/retention-policies/{category}")
    @Operation(summary = "Update retention policy",
        description = "Updates the retention period for a specific data category. "
                    + "The value must fall within the configured min/max bounds.")
    @RequiresPermission("action.compliance.retention.manage")
    public ResponseEntity<ApiResponse<RetentionPolicyResponse>> updateRetentionPolicy(
            @PathVariable String category,
            @Valid @RequestBody UpdateRetentionPolicyRequest request) {
        UUID clientId = tenantValidator.getCurrentClientId();
        log.info("Updating retention policy: clientId={}, category={}, days={}",
                clientId, category, request.retentionDays());

        RetentionPolicy updated = dataRetentionService.updateRetentionPolicy(
                clientId, category, request.retentionDays());
        return ResponseEntity.ok(ApiResponse.success(
                RetentionPolicyResponse.from(updated), "Retention policy updated"));
    }

    @GetMapping("/retention-policies/defaults")
    @Operation(summary = "Get system default retention policies",
        description = "Returns the system-wide default retention policies. TENX_ADMIN only.")
    @RequiresPermission("action.tenx.compliance.manage")
    public ResponseEntity<ApiResponse<List<RetentionPolicyResponse>>> getSystemDefaults() {
        List<RetentionPolicy> defaults = dataRetentionService.getSystemDefaults();
        List<RetentionPolicyResponse> response = defaults.stream()
                .map(RetentionPolicyResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(response, "System default policies retrieved"));
    }

    @GetMapping("/retention-policies/bounds")
    @Operation(summary = "Get retention policy bounds",
        description = "Returns the min/max retention day bounds for each data category.")
    @RequiresPermission("action.compliance.retention.view")
    public ResponseEntity<ApiResponse<RetentionBoundsResponse>> getRetentionBounds() {
        Map<String, int[]> bounds = dataRetentionService.getRetentionBounds();
        return ResponseEntity.ok(ApiResponse.success(
                RetentionBoundsResponse.from(bounds), "Retention bounds retrieved"));
    }

    // -------------------------------------------------------------------------
    // Sub-Processors
    // -------------------------------------------------------------------------

    @GetMapping("/sub-processors")
    @Operation(summary = "Get sub-processor registry",
        description = "Returns the list of all sub-processors used for data processing. "
                    + "Required by GDPR Article 28 for transparency on data processing partners.")
    @RequiresPermission("action.compliance.subprocessors.view")
    public ResponseEntity<ApiResponse<List<SubProcessorResponse>>> getSubProcessors() {
        List<SubProcessor> processors = subProcessorService.getAll();
        List<SubProcessorResponse> response = processors.stream()
                .map(SubProcessorResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(response, "Sub-processors retrieved"));
    }

    @PostMapping("/sub-processors")
    @Operation(summary = "Create sub-processor",
        description = "Adds a new sub-processor to the registry. TENX_ADMIN only.")
    @RequiresPermission("action.tenx.compliance.manage")
    public ResponseEntity<ApiResponse<SubProcessorResponse>> createSubProcessor(
            @Valid @RequestBody CreateSubProcessorRequest request) {
        SubProcessor created = subProcessorService.create(
                request.name(), request.purpose(), request.dataProcessed(),
                request.location(), request.dpaStatus(), request.sccStatus());
        return ResponseEntity.ok(ApiResponse.success(
                SubProcessorResponse.from(created), "Sub-processor created"));
    }

    @PutMapping("/sub-processors/{id}")
    @Operation(summary = "Update sub-processor",
        description = "Updates an existing sub-processor in the registry. TENX_ADMIN only.")
    @RequiresPermission("action.tenx.compliance.manage")
    public ResponseEntity<ApiResponse<SubProcessorResponse>> updateSubProcessor(
            @PathVariable UUID id,
            @Valid @RequestBody CreateSubProcessorRequest request) {
        SubProcessor updated = subProcessorService.update(
                id, request.name(), request.purpose(), request.dataProcessed(),
                request.location(), request.dpaStatus(), request.sccStatus());
        return ResponseEntity.ok(ApiResponse.success(
                SubProcessorResponse.from(updated), "Sub-processor updated"));
    }

    @DeleteMapping("/sub-processors/{id}")
    @Operation(summary = "Delete sub-processor",
        description = "Removes a sub-processor from the registry. TENX_ADMIN only.")
    @RequiresPermission("action.tenx.compliance.manage")
    public ResponseEntity<ApiResponse<Void>> deleteSubProcessor(@PathVariable UUID id) {
        subProcessorService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Sub-processor deleted"));
    }

    // -------------------------------------------------------------------------
    // Breach Incidents
    // -------------------------------------------------------------------------

    @GetMapping("/breach-incidents")
    @Operation(summary = "Get breach incidents",
        description = "Returns all active (non-closed) breach incidents. TENX_ADMIN only. "
                    + "GDPR Article 33 requires reporting within 72 hours of detection.")
    @RequiresPermission("action.tenx.compliance.manage")
    public ResponseEntity<ApiResponse<List<BreachIncidentResponse>>> getBreachIncidents() {
        List<BreachIncident> incidents = breachIncidentService.getActiveIncidents();
        List<BreachIncidentResponse> response = incidents.stream()
                .map(BreachIncidentResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(response, "Breach incidents retrieved"));
    }

    @PostMapping("/breach-incidents")
    @Operation(summary = "Create breach incident",
        description = "Records a new data breach incident. TENX_ADMIN only. "
                    + "Per GDPR Article 33, breaches must be reported to the supervisory authority "
                    + "within 72 hours of becoming aware.")
    @RequiresPermission("action.tenx.compliance.manage")
    public ResponseEntity<ApiResponse<BreachIncidentResponse>> createBreachIncident(
            @Valid @RequestBody CreateBreachIncidentRequest request) {
        UUID currentUserId = tenantValidator.getCurrentUserId();
        BreachIncident created = breachIncidentService.create(
                request.description(), request.severity(), request.dataAffected(),
                request.detectedAt(), currentUserId);
        return ResponseEntity.ok(ApiResponse.success(
                BreachIncidentResponse.from(created), "Breach incident recorded"));
    }
}
