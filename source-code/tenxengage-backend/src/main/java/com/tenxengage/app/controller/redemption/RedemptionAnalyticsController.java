package com.tenxengage.app.controller.redemption;

import com.tenxengage.app.dto.response.ErrorResponse;
import com.tenxengage.app.dto.response.redemption.RedemptionAnalyticsSummaryResponse;
import com.tenxengage.app.security.AnalyticsExportRateLimiter;
import com.tenxengage.app.security.AnalyticsExportRateLimiter.RateLimitResult;
import com.tenxengage.app.security.RequiresPermission;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.redemption.RedemptionAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

@Tag(name = "Redemption Analytics")
@RestController
@RequestMapping("/api/v1/redemption/analytics")
@Validated
public class RedemptionAnalyticsController {

    private static final Logger log = LoggerFactory.getLogger(RedemptionAnalyticsController.class);

    private final RedemptionAnalyticsService analyticsService;
    private final AnalyticsExportRateLimiter exportRateLimiter;
    private final TenantValidator tenantValidator;

    public RedemptionAnalyticsController(RedemptionAnalyticsService analyticsService,
                                         AnalyticsExportRateLimiter exportRateLimiter,
                                         TenantValidator tenantValidator) {
        this.analyticsService = analyticsService;
        this.exportRateLimiter = exportRateLimiter;
        this.tenantValidator = tenantValidator;
    }

    /**
     * Returns the redemption analytics summary for the authenticated CLIENT_ADMIN tenant.
     *
     * <p>Lifetime metrics (redemptionRates, unredeemedBalances) are unaffected by the date
     * window. Windowed metrics (failedCancelledRates, totalRedemptionCount) reflect the
     * selected date range. Response is cached for 60 s in Redis (AC-4).</p>
     *
     * <p>Rate limited to 10 req/min per user via the existing {@code RateLimitFilter}.</p>
     */
    @GetMapping
    @RequiresPermission("action.redemption.view_analytics")
    @Operation(
            summary = "Get redemption analytics summary",
            description = "Returns four metric groups for the authenticated tenant. "
                    + "Lifetime metrics (redemptionRates, unredeemedBalances) are unaffected by the date "
                    + "window. Windowed metrics (failedCancelledRates, totalRedemptionCount) are scoped "
                    + "to the dateFrom/dateTo window. Response served from Redis cache (TTL 60 s)."
    )
    public ResponseEntity<RedemptionAnalyticsSummaryResponse> getAnalyticsSummary(
            @Parameter(description = "Start of the date window (ISO 8601: YYYY-MM-DD). "
                    + "Defaults to today minus 30 days. Affects windowed metrics only.")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,

            @Parameter(description = "End of the date window (ISO 8601: YYYY-MM-DD). "
                    + "Defaults to today. Must not be before dateFrom. Maximum range is 730 days.")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate effectiveDateFrom = dateFrom != null ? dateFrom : today.minusDays(30);
        LocalDate effectiveDateTo = dateTo != null ? dateTo : today;

        RedemptionAnalyticsSummaryResponse response =
                analyticsService.getAnalyticsSummary(effectiveDateFrom, effectiveDateTo);

        return ResponseEntity.ok(response);
    }

    /**
     * Exports all unredeemed wallet balances for the authenticated tenant as a CSV attachment.
     *
     * <p>Rate limited to {@value AnalyticsExportRateLimiter#MAX_REQUESTS_PER_WINDOW} requests per
     * tenant per {@value AnalyticsExportRateLimiter#WINDOW_SECONDS} seconds. A 429 response
     * includes a {@code Retry-After} header with the seconds until the window resets (AC-4).</p>
     *
     * <p>Every successful 200 response emits an audit record:
     * {@code action=DATA_EXPORTED}, {@code resourceType=REDEMPTION_ANALYTICS_EXPORT} (AC-3).</p>
     *
     * <p>Not cached — always reflects a live wallet snapshot (FR-07.9).</p>
     */
    @GetMapping("/export")
    @RequiresPermission("action.redemption.view_analytics")
    @Operation(
            summary = "Export unredeemed balances as CSV",
            description = "CLIENT_ADMIN downloads a CSV snapshot of all RewardWallet rows in the tenant. "
                    + "One row per wallet. Columns: userId, userName, companyId, companyName, "
                    + "currencyType, availableBalance, reservedBalance. "
                    + "Rate limited to 3 req/min per tenant; 429 includes Retry-After header. "
                    + "Every download is audited (action=DATA_EXPORTED, resourceType=REDEMPTION_ANALYTICS_EXPORT)."
    )
    public ResponseEntity<Object> exportUnredeemedBalances(HttpServletRequest request) {
        UUID clientId = tenantValidator.getCurrentClientId();

        RateLimitResult rateLimit = exportRateLimiter.tryAcquireWithRetryAfter(clientId);
        if (!rateLimit.allowed()) {
            log.warn("step=rate_limit_exceeded endpoint=/api/v1/redemption/analytics/export tenantId={}",
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

        byte[] csv = analyticsService.exportUnredeemedBalances();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("redemption-unredeemed-balances.csv")
                .build());

        return ResponseEntity.ok().headers(headers).body(csv);
    }
}
