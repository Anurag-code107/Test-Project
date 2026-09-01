package com.tenxengage.app.controller;

import com.tenxengage.app.audit.Audited;
import com.tenxengage.app.dto.request.SaveFiscalYearConfigRequest;
import com.tenxengage.app.dto.response.FiscalYearConfigResponse;
import com.tenxengage.app.dto.response.FiscalYearLabelResponse;
import com.tenxengage.app.security.RequiresPermission;
import com.tenxengage.app.service.FiscalYearConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fiscal-year-configs")
@Tag(name = "Fiscal Year Configs", description = "Manage fiscal year and quarter definitions")
@Validated
public class FiscalYearConfigController {

    private final FiscalYearConfigService fiscalYearConfigService;

    public FiscalYearConfigController(FiscalYearConfigService fiscalYearConfigService) {
        this.fiscalYearConfigService = fiscalYearConfigService;
    }

    @GetMapping
    @Operation(summary = "List all fiscal year configs for current client")
    @RequiresPermission("action.fiscal_year.view")
    public ResponseEntity<List<FiscalYearConfigResponse>> listConfigs() {
        return ResponseEntity.ok(fiscalYearConfigService.listConfigs());
    }

    @GetMapping("/labels")
    @Operation(summary = "List fiscal year labels for dropdowns")
    @RequiresPermission("action.fiscal_year.view")
    public ResponseEntity<List<FiscalYearLabelResponse>> listLabels() {
        return ResponseEntity.ok(fiscalYearConfigService.listLabels());
    }

    @GetMapping("/current")
    @Operation(summary = "Get the fiscal year config containing today's date")
    @RequiresPermission("action.fiscal_year.view")
    public ResponseEntity<FiscalYearConfigResponse> getCurrentConfig() {
        return ResponseEntity.ok(fiscalYearConfigService.getCurrentConfig());
    }

    @GetMapping("/by-label/{label}")
    @Operation(summary = "Get a fiscal year config by label")
    @RequiresPermission("action.fiscal_year.view")
    public ResponseEntity<FiscalYearConfigResponse> getConfigByLabel(@PathVariable String label) {
        return ResponseEntity.ok(fiscalYearConfigService.getConfigByLabel(label));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a fiscal year config by ID")
    @RequiresPermission("action.fiscal_year.view")
    public ResponseEntity<FiscalYearConfigResponse> getConfig(@PathVariable UUID id) {
        return ResponseEntity.ok(fiscalYearConfigService.getConfig(id));
    }

    @PostMapping
    @Operation(summary = "Create a fiscal year config")
    @RequiresPermission("action.fiscal_year.manage")
    @Audited(action = "Created", resourceType = "FISCAL_YEAR_CONFIG",
             resourceName = "#result.body.label", resourceId = "#result.body.id.toString()")
    public ResponseEntity<FiscalYearConfigResponse> createConfig(
            @Valid @RequestBody SaveFiscalYearConfigRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(fiscalYearConfigService.createConfig(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a fiscal year config")
    @RequiresPermission("action.fiscal_year.manage")
    @Audited(action = "Edited", resourceType = "FISCAL_YEAR_CONFIG",
             resourceName = "#result.body.label", resourceId = "#result.body.id.toString()")
    public ResponseEntity<FiscalYearConfigResponse> updateConfig(
            @PathVariable UUID id,
            @Valid @RequestBody SaveFiscalYearConfigRequest request) {
        return ResponseEntity.ok(fiscalYearConfigService.updateConfig(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a fiscal year config")
    @RequiresPermission("action.fiscal_year.manage")
    @Audited(action = "Deleted", resourceType = "FISCAL_YEAR_CONFIG",
             resourceId = "#id.toString()", description = "Deleted fiscal year config")
    public ResponseEntity<Void> deleteConfig(@PathVariable UUID id) {
        fiscalYearConfigService.deleteConfig(id);
        return ResponseEntity.noContent().build();
    }
}
