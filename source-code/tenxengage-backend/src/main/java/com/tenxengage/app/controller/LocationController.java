package com.tenxengage.app.controller;

import com.tenxengage.app.audit.Audited;
import com.tenxengage.app.dto.request.CreateLocationLevelRequest;
import com.tenxengage.app.dto.request.CreateLocationValueRequest;
import com.tenxengage.app.dto.request.UpdateLocationLevelRequest;
import com.tenxengage.app.dto.request.UpdateLocationLevelSettingsRequest;
import com.tenxengage.app.dto.request.UpdateLocationValueRequest;
import com.tenxengage.app.dto.response.LocationFilterOptionsResponse;
import com.tenxengage.app.dto.response.LocationHierarchyResponse;
import com.tenxengage.app.dto.response.LocationLevelResponse;
import com.tenxengage.app.dto.response.LocationValueResponse;
import com.tenxengage.app.security.RequiresPermission;
import com.tenxengage.app.service.LocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/location-levels")
@Tag(name = "Location Hierarchy", description = "Manage location hierarchy levels and values")
@Validated
public class LocationController {

    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    @GetMapping
    @Operation(summary = "Get location hierarchy", description = "Returns all levels and the location tree")
    @RequiresPermission("action.location.view")
    public ResponseEntity<LocationHierarchyResponse> getHierarchy() {
        return ResponseEntity.ok(locationService.getHierarchy());
    }

    @PostMapping
    @Operation(summary = "Create a location level")
    @RequiresPermission("action.location.manage")
    @Audited(action = "Created", resourceType = "LOCATION_LEVEL",
             resourceName = "#result.body.name", resourceId = "#result.body.id.toString()")
    public ResponseEntity<LocationLevelResponse> createLevel(
            @Valid @RequestBody CreateLocationLevelRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(locationService.createLevel(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a location level")
    @RequiresPermission("action.location.manage")
    @Audited(action = "Edited", resourceType = "LOCATION_LEVEL",
             resourceName = "#result.body.name", resourceId = "#result.body.id.toString()")
    public ResponseEntity<LocationLevelResponse> updateLevel(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateLocationLevelRequest request) {
        return ResponseEntity.ok(locationService.updateLevel(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a location level")
    @RequiresPermission("action.location.manage")
    @Audited(action = "Deleted", resourceType = "LOCATION_LEVEL",
             resourceId = "#id.toString()", description = "Deleted location level")
    public ResponseEntity<Void> deleteLevel(@PathVariable UUID id) {
        locationService.deleteLevel(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/values")
    @Operation(summary = "Create a location value")
    @RequiresPermission("action.location.manage")
    @Audited(action = "Created", resourceType = "LOCATION_VALUE",
             resourceName = "#result.body.name", resourceId = "#result.body.id.toString()")
    public ResponseEntity<LocationValueResponse> createValue(
            @Valid @RequestBody CreateLocationValueRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(locationService.createValue(request));
    }

    @PutMapping("/values/{id}")
    @Operation(summary = "Update a location value")
    @RequiresPermission("action.location.manage")
    @Audited(action = "Edited", resourceType = "LOCATION_VALUE",
             resourceName = "#result.body.name", resourceId = "#result.body.id.toString()")
    public ResponseEntity<LocationValueResponse> updateValue(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateLocationValueRequest request) {
        return ResponseEntity.ok(locationService.updateValue(id, request));
    }

    @DeleteMapping("/values/{id}")
    @Operation(summary = "Delete a location value")
    @RequiresPermission("action.location.manage")
    @Audited(action = "Deleted", resourceType = "LOCATION_VALUE",
             resourceId = "#id.toString()", description = "Deleted location value")
    public ResponseEntity<Void> deleteValue(@PathVariable UUID id) {
        locationService.deleteValue(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/settings")
    @Operation(summary = "Update location level enablement settings",
               description = "Toggle whether this level appears in the incentive builder and/or admin page filters")
    @RequiresPermission("action.location.manage")
    @Audited(action = "Edited", resourceType = "LOCATION_LEVEL",
             resourceName = "#result.body.name", resourceId = "#id.toString()")
    public ResponseEntity<LocationLevelResponse> updateLevelSettings(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateLocationLevelSettingsRequest request) {
        return ResponseEntity.ok(locationService.updateLevelSettings(id, request));
    }

    @GetMapping("/builder-options")
    @Operation(summary = "Get builder-enabled levels",
               description = "Returns location levels marked for use in the incentive builder")
    @RequiresPermission("action.location.view")
    public ResponseEntity<List<LocationLevelResponse>> getBuilderOptions() {
        return ResponseEntity.ok(locationService.getBuilderEnabledLevels());
    }

    @GetMapping("/filter-options")
    @Operation(summary = "Get filter-enabled levels with values",
               description = "Returns location levels marked for use in admin page filters, with their values")
    @RequiresPermission("action.location.view")
    public ResponseEntity<LocationFilterOptionsResponse> getFilterOptions() {
        return ResponseEntity.ok(locationService.getFilterOptions());
    }
}
