package com.tenxengage.app.service.redemption;

import com.tenxengage.app.dto.request.redemption.RedemptionHistoryFilters;
import com.tenxengage.app.dto.request.redemption.TriggerExportRequest;
import com.tenxengage.app.dto.response.redemption.RedemptionExportJobDetailResponse;
import com.tenxengage.app.dto.response.redemption.RedemptionExportJobResponse;
import com.tenxengage.app.entity.RedemptionRequest;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.redemption.ExportFormat;
import com.tenxengage.app.entity.enums.redemption.ExportScope;
import com.tenxengage.app.entity.enums.redemption.RedemptionExportStatus;
import com.tenxengage.app.entity.redemption.RedemptionExportJob;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.repository.redemption.RedemptionExportJobRepository;
import com.tenxengage.app.repository.redemption.RedemptionHistoryRepository;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.FileStorageService;
import com.tenxengage.app.service.PermissionService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RedemptionExportService {

    private static final Logger log = LoggerFactory.getLogger(RedemptionExportService.class);
    private static final int SYNC_THRESHOLD = 1_000;
    // Hard cap: exports larger than this are rejected to avoid OOM and silent file truncation.
    // Raise this limit (and implement chunked streaming) when large-tenant exports are needed.
    private static final int MAX_EXPORT_ROWS = 50_000;
    private static final int EXPORT_URL_TTL_MINUTES = 24 * 60;

    private final RedemptionHistoryRepository historyRepository;
    private final RedemptionExportJobRepository exportJobRepository;
    private final UserRepository userRepository;
    private final FileStorageService storageService;
    private final PermissionService permissionService;
    private final TenantValidator tenantValidator;

    @Autowired @Lazy
    private RedemptionExportService self;

    public RedemptionExportService(RedemptionHistoryRepository historyRepository,
                                   RedemptionExportJobRepository exportJobRepository,
                                   UserRepository userRepository,
                                   FileStorageService storageService,
                                   PermissionService permissionService,
                                   TenantValidator tenantValidator) {
        this.historyRepository = historyRepository;
        this.exportJobRepository = exportJobRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
        this.permissionService = permissionService;
        this.tenantValidator = tenantValidator;
    }

    @Transactional
    public ExportResult triggerExport(TriggerExportRequest request, UUID userId) {
        UUID clientId = tenantValidator.getCurrentClientId();
        RedemptionHistoryFilters filters = new RedemptionHistoryFilters(
                request.status(), request.category(), request.dateFrom(), request.dateTo());

        if (filters.dateFrom() != null && filters.dateTo() != null
                && filters.dateFrom().isAfter(filters.dateTo())) {
            throw new BusinessRuleException("dateFrom must not be after dateTo");
        }

        java.util.Set<String> perms = permissionService.resolveEffectivePermissions(userId);
        boolean canTenant = perms.contains("action.redemption.view_all_history");
        // Company export requires explicit company-admin permission (PARTNER_ADMIN, not PARTNER_SELLER)
        // to prevent sellers from accidentally exporting all company-wallet redemptions.
        boolean canCompany = perms.contains("action.redemption.redeem_company");
        UUID currentCompanyId = tenantValidator.getCurrentPartnerCompanyId();

        // Honor the scope the client explicitly requested (the tab the export was triggered from),
        // validated against the caller's permissions. Without this, a user who *can* export a wider
        // scope (e.g. a Partner Admin with redeem_company) would always be forced into that wider
        // scope — so exporting from the Personal tab silently ran as COMPANY and returned nothing
        // when the company had no company-wallet redemptions. An unauthorized scope is narrowed to
        // what the caller may see, never widened. A null scope preserves the legacy "widest
        // permitted scope" behavior for callers that predate the scope field.
        ExportScope requested = request.scope();
        boolean isTenantExport;
        boolean isCompanyExport;
        if (requested == ExportScope.PERSONAL) {
            isTenantExport = false;
            isCompanyExport = false;
        } else if (requested == ExportScope.COMPANY) {
            isTenantExport = false;
            isCompanyExport = canCompany && currentCompanyId != null;
        } else {
            // ALL_TENANT or unspecified → widest scope the caller is permitted.
            isTenantExport = canTenant;
            isCompanyExport = !canTenant && canCompany && currentCompanyId != null;
        }
        UUID partnerCompanyId = isCompanyExport ? currentCompanyId : null;
        String scope = isTenantExport ? "ALL_TENANT" : (isCompanyExport ? "COMPANY" : "PERSONAL");

        // ALL_TENANT-only name filters (mirror the All-Redemptions list search). Ignored otherwise.
        String tenantUserName = isTenantExport ? blankToNull(request.userName()) : null;
        String tenantCompanyName = isTenantExport ? blankToNull(request.companyName()) : null;

        long count;
        if (isTenantExport) {
            // Use the tenant find (page total) so the count honors the name filters, matching the
            // COMPANY path pattern — the old countTenantHistory ignored user/company scoping.
            count = historyRepository.findTenantHistory(clientId, null, null, tenantUserName, tenantCompanyName,
                    filters.status(), filters.category(), filters.dateFromInstant(), filters.dateToInstant(),
                    org.springframework.data.domain.PageRequest.of(0, 1)).getTotalElements();
        } else if (isCompanyExport) {
            // Use the company-scoped count (wallet-based, not user-based) so the sync/async
            // threshold and MAX_EXPORT_ROWS cap reflect the actual exported company scope.
            count = historyRepository.findCompanyHistoryByPartnerCompany(
                    clientId, partnerCompanyId, filters.status(), filters.category(),
                    filters.dateFromInstant(), filters.dateToInstant(),
                    org.springframework.data.domain.PageRequest.of(0, 1)).getTotalElements();
        } else {
            count = historyRepository.countPersonalHistory(userId, clientId,
                    filters.status(), filters.category(), filters.dateFromInstant(), filters.dateToInstant());
        }

        if (count == 0) {
            throw new BusinessRuleException("No records match the selected filters");
        }

        if (count > MAX_EXPORT_ROWS) {
            throw new BusinessRuleException(
                    "Export exceeds the maximum of " + MAX_EXPORT_ROWS + " rows. Apply narrower filters.");
        }

        if (count <= SYNC_THRESHOLD) {
            Page<RedemptionRequest> page;
            if (isTenantExport) {
                page = historyRepository.findTenantHistory(clientId, null, null, tenantUserName, tenantCompanyName,
                        filters.status(), filters.category(), filters.dateFromInstant(), filters.dateToInstant(),
                        PageRequest.of(0, SYNC_THRESHOLD, Sort.by(Sort.Direction.DESC, "submittedAt")));
            } else if (isCompanyExport) {
                page = historyRepository.findCompanyHistoryByPartnerCompany(clientId, partnerCompanyId,
                        filters.status(), filters.category(), filters.dateFromInstant(), filters.dateToInstant(),
                        PageRequest.of(0, SYNC_THRESHOLD, Sort.by(Sort.Direction.DESC, "submittedAt")));
            } else {
                page = historyRepository.findPersonalHistory(userId, clientId,
                        filters.status(), filters.category(), filters.dateFromInstant(), filters.dateToInstant(),
                        PageRequest.of(0, SYNC_THRESHOLD, Sort.by(Sort.Direction.DESC, "submittedAt")));
            }
            byte[] bytes = generateFile(page.getContent(), request.format());
            return new SyncExportResult(bytes, request.format());
        }

        User requestedBy = userRepository.findByIdAndClientId(userId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        Map<String, Object> filterSnapshot = buildFilterSnapshot(request);
        filterSnapshot.put("scope", scope);
        // Persist the authorized partnerCompanyId at trigger time — the async worker must
        // not re-resolve via job.getRequestedBy().getPartnerCompanyId() since the user can
        // be reassigned to another company between commit and job processing.
        if (isCompanyExport) {
            filterSnapshot.put("partnerCompanyId", partnerCompanyId.toString());
        }

        RedemptionExportJob job = RedemptionExportJob.builder()
                .clientId(clientId)
                .requestedBy(requestedBy)
                .format(request.format())
                .scope(scope)
                .filterSnapshot(filterSnapshot)
                .build();
        RedemptionExportJob saved = exportJobRepository.save(job);

        // Defer dispatch to after-commit — the @Async worker needs the job row to be visible
        // in the DB; dispatching inside the transaction would start the worker before the
        // row is committed, causing findById to return empty and leaving the job in PENDING.
        final UUID jobId = saved.getId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    self.processExportJobAsync(jobId);
                }
            });
        } else {
            // No active transaction (e.g. test context) — fire directly
            self.processExportJobAsync(jobId);
        }

        return new AsyncExportResult(saved.getId());
    }

    @Async
    public void processExportJobAsync(UUID jobId) {
        self.processExportJob(jobId);
    }

    @Transactional
    public void processExportJob(UUID jobId) {
        RedemptionExportJob job = exportJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("RedemptionExportJob", "id", jobId));

        job.setStatus(RedemptionExportStatus.PROCESSING);
        exportJobRepository.save(job);

        try {
            UUID clientId = job.getClientId();
            UUID userId = job.getRequestedBy().getId();

            Map<String, Object> snapshot = job.getFilterSnapshot();
            RedemptionHistoryFilters filters = filtersFromSnapshot(snapshot);
            String snapUserName = blankToNull((String) snapshot.get("userName"));
            String snapCompanyName = blankToNull((String) snapshot.get("companyName"));

            String scopeStr = job.getScope();
            boolean isTenantScope = "ALL_TENANT".equals(scopeStr);
            boolean isCompanyScope = "COMPANY".equals(scopeStr);
            // MAX_EXPORT_ROWS is enforced at trigger time; use it as the page limit here
            // to stay consistent — the job should never have been created above this cap.
            Page<RedemptionRequest> page;
            if (isTenantScope) {
                page = historyRepository.findTenantHistory(clientId, null, null, snapUserName, snapCompanyName,
                        filters.status(), filters.category(),
                        filters.dateFromInstant(), filters.dateToInstant(),
                        PageRequest.of(0, MAX_EXPORT_ROWS, Sort.by(Sort.Direction.DESC, "submittedAt")));
            } else if (isCompanyScope) {
                // Read from snapshot — immutable authorized scope set at trigger time,
                // so re-assignment of the requester to another company has no effect.
                String pcIdStr = (String) snapshot.get("partnerCompanyId");
                UUID partnerCompanyId = pcIdStr != null ? UUID.fromString(pcIdStr)
                        : job.getRequestedBy().getPartnerCompanyId();
                page = historyRepository.findCompanyHistoryByPartnerCompany(clientId, partnerCompanyId,
                        filters.status(), filters.category(),
                        filters.dateFromInstant(), filters.dateToInstant(),
                        PageRequest.of(0, MAX_EXPORT_ROWS, Sort.by(Sort.Direction.DESC, "submittedAt")));
            } else {
                page = historyRepository.findPersonalHistory(userId, clientId,
                        filters.status(), filters.category(),
                        filters.dateFromInstant(), filters.dateToInstant(),
                        PageRequest.of(0, MAX_EXPORT_ROWS, Sort.by(Sort.Direction.DESC, "submittedAt")));
            }

            // Fail if the live row count now exceeds the cap — new records may have been
            // added between trigger-time validation and this worker run.
            if (page.getTotalElements() > MAX_EXPORT_ROWS) {
                throw new RuntimeException(
                        "Live row count (" + page.getTotalElements() + ") exceeds MAX_EXPORT_ROWS ("
                        + MAX_EXPORT_ROWS + ") — apply narrower filters and retry");
            }

            byte[] bytes = generateFile(page.getContent(), job.getFormat());
            String ext = job.getFormat() == ExportFormat.CSV ? "csv" : "xlsx";
            String fileKey = "exports/" + clientId + "/" + jobId + "." + ext;
            String contentType = job.getFormat() == ExportFormat.CSV
                    ? "text/csv" : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

            storageService.upload(fileKey, new ByteArrayInputStream(bytes), bytes.length, contentType);

            job.setStatus(RedemptionExportStatus.COMPLETED);
            job.setFileKey(fileKey);
            job.setRowCount(page.getNumberOfElements());
            job.setExpiresAt(Instant.now().plusSeconds(EXPORT_URL_TTL_MINUTES * 60L));
            exportJobRepository.save(job);

            log.info("[step=export_completed] jobId={} rowCount={}", jobId, page.getNumberOfElements());
        } catch (Exception e) {
            job.setStatus(RedemptionExportStatus.FAILED);
            job.setFailureReason("Export generation failed: " + java.util.Objects.toString(e.getMessage(), e.getClass().getSimpleName()));
            exportJobRepository.save(job);
            log.error("[step=export_failed] jobId={}", jobId, e);
        }
    }

    @Transactional(readOnly = true)
    public RedemptionExportJobResponse getExportJob(UUID jobId, UUID userId) {
        UUID clientId = tenantValidator.getCurrentClientId();
        RedemptionExportJob job = exportJobRepository.findByIdAndClientId(jobId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("RedemptionExportJob", "id", jobId));

        boolean isOwner = job.getRequestedBy().getId().equals(userId);
        boolean hasViewAll = permissionService.resolveEffectivePermissions(userId)
                .contains("action.redemption.view_all_history");
        if (!isOwner && !hasViewAll) {
            throw new ResourceNotFoundException("RedemptionExportJob", "id", jobId);
        }

        return RedemptionExportJobResponse.from(job);
    }

    @Transactional(readOnly = true)
    public RedemptionExportJobDetailResponse getExportJobWithDownloadUrl(UUID jobId, UUID userId) {
        UUID clientId = tenantValidator.getCurrentClientId();
        RedemptionExportJob job = exportJobRepository.findByIdAndClientId(jobId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("RedemptionExportJob", "id", jobId));

        boolean isOwner = job.getRequestedBy().getId().equals(userId);
        boolean hasViewAll = permissionService.resolveEffectivePermissions(userId)
                .contains("action.redemption.view_all_history");
        if (!isOwner && !hasViewAll) {
            throw new ResourceNotFoundException("RedemptionExportJob", "id", jobId);
        }

        String downloadUrl = null;
        boolean isExpired = job.getExpiresAt() != null && Instant.now().isAfter(job.getExpiresAt());
        if (job.getStatus() == RedemptionExportStatus.COMPLETED && job.getFileKey() != null && !isExpired) {
            try {
                downloadUrl = storageService.generatePresignedUrl(job.getFileKey(), EXPORT_URL_TTL_MINUTES);
            } catch (UnsupportedOperationException e) {
                // Local storage does not support presigned URLs — return null so the client
                // remains in polling state rather than following a dead endpoint.
                log.warn("[step=export_no_presigned_url] jobId={} — storage provider does not support presigned URLs", jobId);
                downloadUrl = null;
            }
        }

        return RedemptionExportJobDetailResponse.from(job, downloadUrl);
    }

    private byte[] generateFile(List<RedemptionRequest> requests, ExportFormat format) {
        if (format == ExportFormat.CSV) {
            return generateCsv(requests);
        }
        return generateXlsx(requests);
    }

    private byte[] generateCsv(List<RedemptionRequest> requests) {
        StringBuilder sb = new StringBuilder();
        sb.append("ID,Status,Amount,Currency,Category,Processing Mode,Submitted At,Completed At\n");
        for (RedemptionRequest r : requests) {
            sb.append(r.getId()).append(',')
              .append(r.getStatus().name()).append(',')
              .append(r.getAmount().toPlainString()).append(',')
              .append(r.getCurrencyId() == null ? "" : r.getCurrencyId().toUpperCase()).append(',')
              .append(r.getCategory().name()).append(',')
              .append(r.getProcessingMode().name()).append(',')
              .append(r.getSubmittedAt()).append(',')
              .append(r.getCompletedAt() != null ? r.getCompletedAt() : "").append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] generateXlsx(List<RedemptionRequest> requests) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Redemption History");
            Row header = sheet.createRow(0);
            String[] columns = {"ID", "Status", "Amount", "Currency", "Category", "Processing Mode", "Submitted At", "Completed At"};
            for (int i = 0; i < columns.length; i++) {
                header.createCell(i).setCellValue(columns[i]);
            }
            int rowNum = 1;
            for (RedemptionRequest r : requests) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(r.getId().toString());
                row.createCell(1).setCellValue(r.getStatus().name());
                row.createCell(2).setCellValue(r.getAmount().toPlainString());
                row.createCell(3).setCellValue(r.getCurrencyId() == null ? "" : r.getCurrencyId().toUpperCase());
                row.createCell(4).setCellValue(r.getCategory().name());
                row.createCell(5).setCellValue(r.getProcessingMode().name());
                row.createCell(6).setCellValue(r.getSubmittedAt().toString());
                row.createCell(7).setCellValue(r.getCompletedAt() != null ? r.getCompletedAt().toString() : "");
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate XLSX export", e);
        }
    }

    private Map<String, Object> buildFilterSnapshot(TriggerExportRequest request) {
        Map<String, Object> snapshot = new HashMap<>();
        if (request.status() != null) snapshot.put("status", request.status().name());
        if (request.category() != null) snapshot.put("category", request.category().name());
        if (request.dateFrom() != null) snapshot.put("dateFrom", request.dateFrom().toString());
        if (request.dateTo() != null) snapshot.put("dateTo", request.dateTo().toString());
        if (blankToNull(request.userName()) != null) snapshot.put("userName", request.userName().trim());
        if (blankToNull(request.companyName()) != null) snapshot.put("companyName", request.companyName().trim());
        return snapshot;
    }

    /** Treat blank filter strings as "no filter". */
    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private RedemptionHistoryFilters filtersFromSnapshot(Map<String, Object> snapshot) {
        var status = snapshot.containsKey("status")
                ? com.tenxengage.app.entity.enums.RedemptionStatus.valueOf((String) snapshot.get("status")) : null;
        var category = snapshot.containsKey("category")
                ? com.tenxengage.app.entity.enums.RedemptionCategory.valueOf((String) snapshot.get("category")) : null;
        var dateFrom = snapshot.containsKey("dateFrom")
                ? java.time.LocalDate.parse((String) snapshot.get("dateFrom")) : null;
        var dateTo = snapshot.containsKey("dateTo")
                ? java.time.LocalDate.parse((String) snapshot.get("dateTo")) : null;
        return new RedemptionHistoryFilters(status, category, dateFrom, dateTo);
    }

    public sealed interface ExportResult permits SyncExportResult, AsyncExportResult {}

    public record SyncExportResult(byte[] data, ExportFormat format) implements ExportResult {}

    public record AsyncExportResult(UUID jobId) implements ExportResult {}
}
