package com.tenxengage.app.controller;

import com.tenxengage.app.dto.request.CreateBuilderFieldRequest;
import com.tenxengage.app.dto.request.UpdateBuilderFieldRequest;
import com.tenxengage.app.dto.request.UpdateSectionRequest;
import com.tenxengage.app.dto.response.BuilderConfigResponse;
import com.tenxengage.app.dto.response.BuilderFieldConfigResponse;
import com.tenxengage.app.dto.response.BuilderSectionConfigResponse;
import com.tenxengage.app.dto.response.FieldValueOption;
import com.tenxengage.app.security.RequiresPermission;
import com.tenxengage.app.service.BuilderConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/builder-config")
@Tag(name = "Builder Configuration", description = "Dynamic incentive builder configuration")
public class BuilderConfigController {

    private final BuilderConfigService builderConfigService;

    public BuilderConfigController(BuilderConfigService builderConfigService) {
        this.builderConfigService = builderConfigService;
    }

    @GetMapping("/{incentiveType}")
    @Operation(summary = "Get builder configuration for an incentive type")
    @RequiresPermission("action.incentive.view")
    public ResponseEntity<BuilderConfigResponse> getBuilderConfig(@PathVariable String incentiveType) {
        return ResponseEntity.ok(builderConfigService.getBuilderConfig(incentiveType));
    }

    @PutMapping("/sections/{sectionId}")
    @Operation(summary = "Update a builder section")
    @RequiresPermission("action.builder.manage")
    public ResponseEntity<BuilderSectionConfigResponse> updateSection(
            @PathVariable UUID sectionId, @Valid @RequestBody UpdateSectionRequest request) {
        return ResponseEntity.ok(builderConfigService.updateSection(sectionId, request));
    }

    @PostMapping("/sections/{sectionId}/fields")
    @Operation(summary = "Add a field to a builder section")
    @RequiresPermission("action.builder.manage")
    public ResponseEntity<BuilderFieldConfigResponse> addField(
            @PathVariable UUID sectionId, @Valid @RequestBody CreateBuilderFieldRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(builderConfigService.addField(sectionId, request));
    }

    @PutMapping("/fields/{fieldId}")
    @Operation(summary = "Update a builder field")
    @RequiresPermission("action.builder.manage")
    public ResponseEntity<BuilderFieldConfigResponse> updateField(
            @PathVariable UUID fieldId, @Valid @RequestBody UpdateBuilderFieldRequest request) {
        return ResponseEntity.ok(builderConfigService.updateField(fieldId, request));
    }

    @DeleteMapping("/fields/{fieldId}")
    @Operation(summary = "Remove a builder field")
    @RequiresPermission("action.builder.manage")
    public ResponseEntity<Void> removeField(@PathVariable UUID fieldId) {
        builderConfigService.removeField(fieldId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/fields/{fieldId}/values")
    @Operation(summary = "Resolve dynamic field values with optional context")
    @RequiresPermission("action.incentive.view")
    public ResponseEntity<List<FieldValueOption>> resolveFieldValues(
            @PathVariable UUID fieldId, @RequestParam Map<String, String[]> context) {
        return ResponseEntity.ok(builderConfigService.resolveFieldValues(fieldId, context));
    }
}
