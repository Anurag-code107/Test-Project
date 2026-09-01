package com.tenxengage.app.controller;

import com.tenxengage.app.dto.response.AuditLogResponse;
import com.tenxengage.app.entity.AuditLog;
import com.tenxengage.app.entity.enums.AuditAction;
import com.tenxengage.app.security.RequiresPermission;
import com.tenxengage.app.security.TenantContext;
import com.tenxengage.app.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit-logs")
@Tag(name = "Audit Logs", description = "Activity log endpoints")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    @RequiresPermission("action.activity_log.view")
    @Operation(summary = "Get audit logs", description = "Paginated and filterable audit log entries")
    public ResponseEntity<?> getAuditLogs(
            @RequestParam(required = false) String userType,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {

        UUID clientId = TenantContext.getClientId();

        AuditAction auditAction = null;
        if (action != null && !action.isEmpty()) {
            try {
                auditAction = AuditAction.valueOf(action.toUpperCase().replace(" ", "_"));
            } catch (IllegalArgumentException ignored) {
                // Invalid action filter — return empty results
            }
        }

        Page<AuditLog> results = auditLogService.query(
                clientId, userType, auditAction, dateFrom, dateTo, search, page, pageSize);

        return ResponseEntity.ok(results.map(AuditLogResponse::from));
    }
}
