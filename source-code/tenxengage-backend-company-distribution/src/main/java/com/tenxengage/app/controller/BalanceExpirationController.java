package com.tenxengage.app.controller;

import com.tenxengage.app.audit.Audited;
import com.tenxengage.app.dto.request.UpsertBalanceExpirationPolicyRequest;
import com.tenxengage.app.dto.response.BalanceBreakageReportResponse;
import com.tenxengage.app.dto.response.BalanceExpirationPolicyResponse;
import com.tenxengage.app.dto.response.ErrorResponse;
import com.tenxengage.app.dto.response.ExpiringBalancePreviewResponse;
import com.tenxengage.app.entity.enums.Granularity;
import com.tenxengage.app.security.AnalyticsExportRateLimiter;
import com.tenxengage.app.security.AnalyticsExportRateLimiter.RateLimitResult;
import com.tenxengage.app.security.RequiresPermission;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.redemption.BalanceBreakageReportService;
import com.tenxengage.app.service.redemption.BalanceExpirationPolicyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/redemption/expiration")
@Tag(name = "Balance Expiration", description = "Per-currency reward balance expiration policy management")
@Validated
public class BalanceExpirationController {

    private static final Logger log = LoggerFactory.getLogger(BalanceExpirationController.class);

    private static final String BREAKAGE_PERMISSION = "action.redemption.expiration.view_breakage";

    private final BalanceExpirationPolicyService policyService;
    private final BalanceBreakageReportService breakageReportService;
    private final AnalyticsExportRateLimiter exportRateLimiter;
    private final TenantValidator tenantValidator;

    public BalanceExpirationController(BalanceExpirationPolicyService policyService,
                                        BalanceBreakageReportService breakageReportService,
                                        AnalyticsExportRateLimiter exportRateLimiter,
                                        TenantValidator tenantValidator) {
        this.policyService = policyService;
        this.breakageReportService = breakageReportService;
        this.exportRateLimiter = exportRateLimiter;
        this.tenantValidator = tenantValidator;
    }

    @GetMapping("/policies")
    @RequiresPermission("action.redemption.expiration.configure")
    @Operation(summary = "List balance expiration policies",
            description = "Returns all balance expiration policies for the tenant (at most 4 rows, one per currency type).")
    public ResponseEntity<List<BalanceExpirationPolicyResponse>> getPolicies() {
        return ResponseEntity.ok(policyService.getPolicies());
    }

    @PutMapping("/policies/{currencyId}")
    @RequiresPermission("action.redemption.expiration.configure")
    @Audited(action = "EDITED", resourceType = "BALANCE_EXPIRATION_POLICY",
            description = "Configured balance expiration policy")
    @Operation(summary = "Create or update a balance expiration policy",
            description = "Upserts the expiration policy for a single currency type. " +
                    "Enables or materially changing a policy sets the grace-window anchor. " +
                    "Returns 422 with errorCode for invalid configuration (FR-09.9).")
    public ResponseEntity<BalanceExpirationPolicyResponse> upsertPolicy(
            @PathVariable @Pattern(regexp = "^[a-z]{3,20}$", message = "currencyId must be a lowercase alphabetic currency code") String currencyId,
            @Valid @RequestBody UpsertBalanceExpirationPolicyRequest request) {
        return ResponseEntity.ok(policyService.upsertPolicy(currencyId, request));
    }

    @GetMapping("/expiring-soon")
    @RequiresPermission("action.redemption.expiration.configure")
    @Operation(summary = "Preview balances approaching expiry",
            description = "Returns an aggregate, read-only preview of balances scheduled to expire within a lead window. " +
                    "Aggregate-only: no per-wallet identity surfaced.")
    public ResponseEntity<List<ExpiringBalancePreviewResponse>> getExpiringSoon(
            @Min(value = 1, message = "withinDays must be a positive integer") @Max(value = 3650, message = "withinDays must not exceed 3650 days") @RequestParam(required = false) Integer withinDays,
            @Pattern(regexp = "^[a-z]{3,20}$", message = "currencyId must be a lowercase alphabetic currency code") @RequestParam(required = false) String currencyId) {
        return ResponseEntity.ok(policyService.getExpiringSoon(withinDays, currencyId));
    }

    // ── Breakage report (FR-09.6) ──────────────────────────────────────────────

    @GetMapping("/breakage")
    @RequiresPermission(BREAKAGE_PERMISSION)
    @Operation(
            summary = "Balance expiration breakage report",
            description = "CLIENT_ADMIN retrieves the breakage (expired value) report — aggregated from " +
                    "LedgerEntry rows where entry_type = EXPIRY — broken down by currency type and period. " +
                    "Aggregate-only: no per-user identity. The date range is required and capped at 24 months.")
    public ResponseEntity<BalanceBreakageReportResponse> getBreakage(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Pattern(regexp = "^[a-z]{3,20}$", message = "currencyId must be a lowercase alphabetic currency code")
            @RequestParam(required = false) String currencyId,
            @RequestParam(required = false) Granularity granularity) {
        return ResponseEntity.ok(breakageReportService.getBreakage(from, to, currencyId, granularity));
    }

    // ── Breakage CSV export (FR-09.6) ─────────────────────────────────────────

    /**
     * Exports the breakage report as a CSV attachment (FR-09.6, AC-2, AC-3).
     *
     * <p>Rate limited to {@value AnalyticsExportRateLimiter#MAX_REQUESTS_PER_WINDOW} requests per
     * tenant per {@value AnalyticsExportRateLimiter#WINDOW_SECONDS} seconds via
     * {@link AnalyticsExportRateLimiter}. A 429 includes a {@code Retry-After} header.
     *
     * <p>Audit record ({@code action=DATA_EXPORTED, resourceType=BALANCE_EXPIRY_BREAKAGE_EXPORT})
     * is emitted via {@code @Audited} — fires only on the successful 200 path since 403 is
     * rejected before the method is invoked and 429 returns early before the service call.
     */
    @GetMapping("/breakage/export")
    @RequiresPermission(BREAKAGE_PERMISSION)
    @Operation(
            summary = "Export the balance expiration breakage report as CSV",
            description = "CLIENT_ADMIN downloads the breakage report as a CSV attachment. " +
                    "Rate limited to 3 req/min per tenant; 429 includes Retry-After. " +
                    "Every successful 200 emits an audit record (DATA_EXPORTED / BALANCE_EXPIRY_BREAKAGE_EXPORT). " +
                    "All string cells are escaped against CSV formula injection (CWE-1236).")
    public ResponseEntity<Object> exportBreakageCsv(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Pattern(regexp = "^[a-z]{3,20}$", message = "currencyId must be a lowercase alphabetic currency code")
            @RequestParam(required = false) String currencyId,
            @RequestParam(required = false) Granularity granularity,
            HttpServletRequest request) {

        UUID clientId = tenantValidator.getCurrentClientId();

        RateLimitResult rateLimit = exportRateLimiter.tryAcquireWithRetryAfter(clientId);
        if (!rateLimit.allowed()) {
            log.warn("step=breakage_export_rate_limit_exceeded tenantId={} endpoint=/api/v1/redemption/expiration/breakage/export",
                    clientId);
            HttpHeaders rateLimitHeaders = new HttpHeaders();
            rateLimitHeaders.set("Retry-After", String.valueOf(rateLimit.retryAfterSeconds()));
            ErrorResponse body = ErrorResponse.of(
                    "RATE_LIMIT_EXCEEDED",
                    "Export rate limit exceeded. Retry after " + rateLimit.retryAfterSeconds() + " seconds.",
                    429,
                    request.getRequestURI()
            );
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .headers(rateLimitHeaders)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body);
        }

        String csv = breakageReportService.exportBreakageCsv(from, to, currencyId, granularity);
        byte[] csvBytes = csv.getBytes(StandardCharsets.UTF_8);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("balance-expiration-breakage.csv")
                .build());

        return ResponseEntity.ok().headers(headers).body(csvBytes);
    }
}
