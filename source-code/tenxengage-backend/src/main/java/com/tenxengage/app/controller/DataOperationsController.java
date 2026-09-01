package com.tenxengage.app.controller;

import com.tenxengage.app.dto.request.UpdateSyncScheduleRequest;
import com.tenxengage.app.dto.response.DataUploadResponse;
import com.tenxengage.app.dto.response.SyncScheduleResponse;
import com.tenxengage.app.dto.response.TaggingJobResponse;
import com.tenxengage.app.security.RequiresPermission;
import com.tenxengage.app.service.DataOperationsService;
import com.tenxengage.app.audit.Audited;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/data-operations")
@Tag(name = "Data Operations", description = "File upload, connector pull, tagging, and sync scheduling")
public class DataOperationsController {

    private final DataOperationsService dataOperationsService;

    public DataOperationsController(DataOperationsService dataOperationsService) {
        this.dataOperationsService = dataOperationsService;
    }

    // --- Upload ---

    @GetMapping("/{dataObjectId}/uploads")
    @Operation(summary = "Get upload history for a data object")
    @RequiresPermission("action.data.view")
    public ResponseEntity<List<DataUploadResponse>> getUploadHistory(@PathVariable UUID dataObjectId) {
        return ResponseEntity.ok(dataOperationsService.getUploadHistory(dataObjectId));
    }

    @PostMapping(value = "/{dataObjectId}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a CSV/XLSX file for a data object")
    @RequiresPermission("action.data.upload")
    @Audited(action = "Uploaded", resourceType = "DATA", resourceId = "#dataObjectId.toString()", description = "Uploaded data file")
    public ResponseEntity<DataUploadResponse> uploadFile(
            @PathVariable UUID dataObjectId,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(dataOperationsService.uploadFile(dataObjectId, file));
    }

    @GetMapping("/{dataObjectId}/template")
    @Operation(summary = "Download CSV template for a data object")
    @RequiresPermission("action.data.view")
    public ResponseEntity<byte[]> downloadTemplate(@PathVariable UUID dataObjectId) {
        String csv = dataOperationsService.generateTemplate(dataObjectId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=template.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv.getBytes());
    }

    // --- Connector Pull ---

    @PostMapping("/{dataObjectId}/pull")
    @Operation(summary = "Trigger connector data pull for a data object")
    @RequiresPermission("action.data.pull")
    @Audited(action = "Synced", resourceType = "DATA", resourceId = "#dataObjectId.toString()", description = "Triggered data pull")
    public ResponseEntity<DataUploadResponse> triggerPull(@PathVariable UUID dataObjectId) {
        return ResponseEntity.ok(dataOperationsService.triggerConnectorPull(dataObjectId));
    }

    // --- Tagging ---

    @GetMapping("/tagging/history")
    @Operation(summary = "Get tagging job history")
    @RequiresPermission("action.data.view")
    public ResponseEntity<List<TaggingJobResponse>> getTaggingHistory() {
        return ResponseEntity.ok(dataOperationsService.getTaggingHistory());
    }

    @PostMapping("/tagging/run")
    @Operation(summary = "Trigger eligibility tagging job")
    @RequiresPermission("action.data.tagging")
    @Audited(action = "Synced", resourceType = "DATA", description = "Ran tagging job")
    public ResponseEntity<TaggingJobResponse> runTaggingJob() {
        return ResponseEntity.ok(dataOperationsService.triggerTaggingJob());
    }

    // --- Sync Schedule ---

    @GetMapping("/{dataObjectId}/sync-schedule")
    @Operation(summary = "Get sync schedule for a data object")
    @RequiresPermission("action.data.view")
    public ResponseEntity<SyncScheduleResponse> getSyncSchedule(@PathVariable UUID dataObjectId) {
        return ResponseEntity.ok(dataOperationsService.getSyncSchedule(dataObjectId));
    }

    @PutMapping("/{dataObjectId}/sync-schedule")
    @Operation(summary = "Update sync schedule for a data object")
    @RequiresPermission("action.data.sync")
    @Audited(action = "Edited", resourceType = "DATA", resourceId = "#dataObjectId.toString()", description = "Updated sync schedule")
    public ResponseEntity<SyncScheduleResponse> updateSyncSchedule(
            @PathVariable UUID dataObjectId,
            @Valid @RequestBody UpdateSyncScheduleRequest request) {
        return ResponseEntity.ok(dataOperationsService.updateSyncSchedule(dataObjectId, request));
    }
}
