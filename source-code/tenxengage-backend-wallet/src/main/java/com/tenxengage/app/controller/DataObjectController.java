package com.tenxengage.app.controller;

import com.tenxengage.app.dto.request.ConnectorMappingRequest;
import com.tenxengage.app.dto.request.CreateDataObjectRequest;
import com.tenxengage.app.dto.request.CreateFieldRequest;
import com.tenxengage.app.dto.request.UpdateDataObjectRequest;
import com.tenxengage.app.dto.request.UpdateFieldRequest;
import com.tenxengage.app.dto.response.DataObjectDetailResponse;
import com.tenxengage.app.dto.response.DataObjectFieldResponse;
import com.tenxengage.app.dto.response.DataObjectResponse;
import com.tenxengage.app.dto.response.RuleFieldResponse;
import com.tenxengage.app.service.DataObjectService;
import com.tenxengage.app.audit.Audited;
import com.tenxengage.app.security.RequiresPermission;
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
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/data-objects")
@Tag(name = "Data Objects", description = "Data object and field management")
public class DataObjectController {

    private final DataObjectService dataObjectService;

    public DataObjectController(DataObjectService dataObjectService) {
        this.dataObjectService = dataObjectService;
    }

    // --- Data Object CRUD ---

    @GetMapping
    @Operation(summary = "List data objects for current tenant")
    @RequiresPermission("action.data.view")
    public ResponseEntity<List<DataObjectResponse>> getDataObjects() {
        return ResponseEntity.ok(dataObjectService.getDataObjects());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get data object with fields and mapping")
    @RequiresPermission("action.data.view")
    public ResponseEntity<DataObjectDetailResponse> getDataObject(@PathVariable UUID id) {
        return ResponseEntity.ok(dataObjectService.getDataObject(id));
    }

    @PostMapping
    @Operation(summary = "Create data object")
    @RequiresPermission("action.data.manage")
    @Audited(action = "Created", resourceType = "DATA_OBJECT", resourceName = "#result.body.name", resourceId = "#result.body.id.toString()")
    public ResponseEntity<DataObjectDetailResponse> createDataObject(
            @Valid @RequestBody CreateDataObjectRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(dataObjectService.createDataObject(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update data object metadata")
    @RequiresPermission("action.data.manage")
    @Audited(action = "Edited", resourceType = "DATA_OBJECT", resourceName = "#result.body.name", resourceId = "#result.body.id.toString()")
    public ResponseEntity<DataObjectDetailResponse> updateDataObject(
            @PathVariable UUID id, @Valid @RequestBody UpdateDataObjectRequest request) {
        return ResponseEntity.ok(dataObjectService.updateDataObject(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete data object (non-default only)")
    @RequiresPermission("action.data.manage")
    @Audited(action = "Deleted", resourceType = "DATA_OBJECT", resourceId = "#id.toString()")
    public ResponseEntity<Void> deleteDataObject(@PathVariable UUID id) {
        dataObjectService.deleteDataObject(id);
        return ResponseEntity.noContent().build();
    }

    // --- Field CRUD ---

    @PostMapping("/{id}/fields")
    @Operation(summary = "Add field to data object")
    @RequiresPermission("action.data.manage")
    @Audited(action = "Created", resourceType = "DATA_OBJECT", resourceId = "#id.toString()", description = "Added field")
    public ResponseEntity<DataObjectFieldResponse> addField(
            @PathVariable UUID id, @Valid @RequestBody CreateFieldRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(dataObjectService.addField(id, request));
    }

    @PutMapping("/{id}/fields/{fieldId}")
    @Operation(summary = "Update field")
    @RequiresPermission("action.data.manage")
    @Audited(action = "Edited", resourceType = "DATA_OBJECT", resourceId = "#id.toString()", description = "Updated field")
    public ResponseEntity<DataObjectFieldResponse> updateField(
            @PathVariable UUID id, @PathVariable UUID fieldId,
            @Valid @RequestBody UpdateFieldRequest request) {
        return ResponseEntity.ok(dataObjectService.updateField(id, fieldId, request));
    }

    @DeleteMapping("/{id}/fields/{fieldId}")
    @Operation(summary = "Delete field")
    @RequiresPermission("action.data.manage")
    @Audited(action = "Deleted", resourceType = "DATA_OBJECT", resourceId = "#id.toString()", description = "Deleted field")
    public ResponseEntity<Void> deleteField(@PathVariable UUID id, @PathVariable UUID fieldId) {
        dataObjectService.deleteField(id, fieldId);
        return ResponseEntity.noContent().build();
    }

    // --- Connector Mapping ---

    @PutMapping("/{id}/connector-mapping")
    @Operation(summary = "Set connector field mappings")
    @RequiresPermission("action.data.manage")
    public ResponseEntity<Void> setConnectorMapping(
            @PathVariable UUID id, @Valid @RequestBody ConnectorMappingRequest request) {
        dataObjectService.setConnectorMapping(id, request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/connector-mapping")
    @Operation(summary = "Remove connector mapping")
    @RequiresPermission("action.data.manage")
    public ResponseEntity<Void> removeConnectorMapping(@PathVariable UUID id) {
        dataObjectService.removeConnectorMapping(id);
        return ResponseEntity.noContent().build();
    }

    // --- Rule Fields ---

    @GetMapping("/rule-fields")
    @Operation(summary = "Get all rule-eligible fields for the incentive rules engine")
    @RequiresPermission("action.data.view")
    public ResponseEntity<List<RuleFieldResponse>> getRuleFields(
            @RequestParam(required = false) UUID dataObjectId,
            @RequestParam(required = false) String dataObjectName) {
        return ResponseEntity.ok(dataObjectService.getRuleFields(dataObjectId, dataObjectName));
    }
}
