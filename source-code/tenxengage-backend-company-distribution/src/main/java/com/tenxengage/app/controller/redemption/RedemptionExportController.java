package com.tenxengage.app.controller.redemption;

import com.tenxengage.app.audit.Audited;
import com.tenxengage.app.dto.request.redemption.TriggerExportRequest;
import com.tenxengage.app.dto.response.redemption.RedemptionExportJobDetailResponse;
import com.tenxengage.app.dto.response.redemption.RedemptionExportJobResponse;
import com.tenxengage.app.entity.enums.redemption.ExportFormat;
import com.tenxengage.app.security.ExportRateLimiter;
import com.tenxengage.app.security.RequiresPermission;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.redemption.RedemptionExportService;
import com.tenxengage.app.service.redemption.RedemptionExportService.AsyncExportResult;
import com.tenxengage.app.service.redemption.RedemptionExportService.ExportResult;
import com.tenxengage.app.service.redemption.RedemptionExportService.SyncExportResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/redemption/requests/export")
@Tag(name = "Redemption Export", description = "Redemption transaction data export")
public class RedemptionExportController {

    private final RedemptionExportService exportService;
    private final ExportRateLimiter rateLimiter;
    private final TenantValidator tenantValidator;

    public RedemptionExportController(RedemptionExportService exportService,
                                      ExportRateLimiter rateLimiter,
                                      TenantValidator tenantValidator) {
        this.exportService = exportService;
        this.rateLimiter = rateLimiter;
        this.tenantValidator = tenantValidator;
    }

    @PostMapping
    @RequiresPermission("action.redemption.export")
    @Audited(action = "DATA_EXPORTED", resourceType = "REDEMPTION_EXPORT_JOB",
             description = "Redemption export job triggered")
    @Operation(summary = "Trigger redemption history export")
    public ResponseEntity<?> triggerExport(@Valid @RequestBody TriggerExportRequest request,
                                            HttpServletResponse httpResponse) {
        UUID userId = tenantValidator.getCurrentUserId();

        if (!rateLimiter.tryAcquire(userId)) {
            httpResponse.setHeader("Retry-After", "3600");
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "You've reached the export limit. Please wait before exporting again.");
        }

        ExportResult result = exportService.triggerExport(request, userId);

        if (result instanceof SyncExportResult syncResult) {
            String ext = syncResult.format() == ExportFormat.CSV ? "csv" : "xlsx";
            String contentType = syncResult.format() == ExportFormat.CSV
                    ? "text/csv"
                    : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentDisposition(ContentDisposition.attachment()
                    .filename("redemption-history." + ext).build());
            headers.setContentType(MediaType.parseMediaType(contentType));
            return ResponseEntity.ok().headers(headers).body(syncResult.data());
        }

        AsyncExportResult asyncResult = (AsyncExportResult) result;
        RedemptionExportJobResponse jobResponse = exportService.getExportJob(asyncResult.jobId(), userId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(jobResponse);
    }

    @GetMapping("/{jobId}")
    @RequiresPermission("action.redemption.export")
    @Operation(summary = "Get export job status")
    public ResponseEntity<RedemptionExportJobResponse> getExportJob(@PathVariable UUID jobId) {
        UUID userId = tenantValidator.getCurrentUserId();
        return ResponseEntity.ok(exportService.getExportJob(jobId, userId));
    }

    @GetMapping("/{jobId}/download")
    @RequiresPermission("action.redemption.export")
    @Operation(summary = "Get export job download URL")
    public ResponseEntity<RedemptionExportJobDetailResponse> getExportJobDownload(@PathVariable UUID jobId) {
        UUID userId = tenantValidator.getCurrentUserId();
        return ResponseEntity.ok(exportService.getExportJobWithDownloadUrl(jobId, userId));
    }
}
