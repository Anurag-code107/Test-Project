package com.tenxengage.app.controller.redemption;

import com.tenxengage.app.dto.request.redemption.AdvancedAnalyticsFilter;
import com.tenxengage.app.dto.response.ErrorResponse;
import com.tenxengage.app.dto.response.redemption.AnalyticsRefreshStatusResponse;
import com.tenxengage.app.dto.response.redemption.FailureBreakdownResponse;
import com.tenxengage.app.dto.response.redemption.ItemBreakdownResponse;
import com.tenxengage.app.dto.response.redemption.LiabilityTrendResponse;
import com.tenxengage.app.dto.response.redemption.RedemptionTrendResponse;
import com.tenxengage.app.dto.response.redemption.SegmentBreakdownResponse;
import com.tenxengage.app.dto.response.redemption.TimeToFirstRedemptionResponse;
import com.tenxengage.app.security.AnalyticsExportRateLimiter;
import com.tenxengage.app.security.AnalyticsExportRateLimiter.RateLimitResult;
import com.tenxengage.app.security.RequiresPermission;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.FeatureFlagService;
import com.tenxengage.app.service.redemption.RedemptionAdvancedAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.AccessDeniedException;
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
import java.util.UUID;

/**
 * REST controller for Advanced Redemption Analytics (FR-08.x).
 *
 * <p>Base path: {@code /api/v1/redemption/analytics/advanced}.
 * All endpoints require {@code action.redemption.analytics.advanced}.
 * Feature-flag gate ({@code redemption_analytics_advanced}) is enforced in the service layer.
 *
 * <p>US-05 delivers only the {@code GET /refresh-status} endpoint (BE-3).
 * The remaining dimensional endpoints (item-breakdown, segment-breakdown, etc.) are
 * added in subsequent stories (US-01 through US-04, US-06, US-07).
 */
@Tag(name = "Redemption Advanced Analytics")
@RestController
@RequestMapping("/api/v1/redemption/analytics/advanced")
@Validated
public class RedemptionAdvancedAnalyticsController {

    private static final Logger log = LoggerFactory.getLogger(RedemptionAdvancedAnalyticsController.class);

    private static final String FEATURE_FLAG_KEY = "redemption_analytics_advanced";

    private final RedemptionAdvancedAnalyticsService advancedAnalyticsService;
    private final AnalyticsExportRateLimiter exportRateLimiter;
    private final TenantValidator tenantValidator;
    private final FeatureFlagService featureFlagService;

    public RedemptionAdvancedAnalyticsController(RedemptionAdvancedAnalyticsService advancedAnalyticsService,
                                                  AnalyticsExportRateLimiter exportRateLimiter,
                                                  TenantValidator tenantValidator,
                                                  FeatureFlagService featureFlagService) {
        this.advancedAnalyticsService = advancedAnalyticsService;
        this.exportRateLimiter = exportRateLimiter;
        this.tenantValidator = tenantValidator;
        this.featureFlagService = featureFlagService;
    }

    /**
     * Returns the catalog-item redemption breakdown for the authenticated tenant (FR-08.1).
     *
     * <p>Items are ranked by {@code totalRedeemedCount} descending. An optional {@code region}
     * filter constrains results to redemptions by partners in the specified region.
     *
     * <p>Date range is capped at 365 days; missing dates default to last 30 days / today.
     * Response is Redis-cached 60 seconds keyed on tenant + filter combination (AC-4).
     *
     * @param dateFrom inclusive start date (ISO 8601; defaults to today − 30 days)
     * @param dateTo   inclusive end date   (ISO 8601; defaults to today)
     * @param region   optional region filter; null means all regions
     * @return 200 with {@link ItemBreakdownResponse}; 422 if span exceeds 365 days; 403 if no permission
     */
    @GetMapping("/item-breakdown")
    @RequiresPermission("action.redemption.analytics.advanced")
    @Operation(
            summary = "Redemption breakdown by catalog item",
            description = "Returns redemption metrics broken down by catalog item, ranked by "
                    + "totalRedeemedCount descending. Filterable by date range and region. "
                    + "Redis-cached 60 s per tenant + filter combination. "
                    + "Returns 422 when date span exceeds 365 days; 403 when permission or flag absent."
    )
    public ResponseEntity<ItemBreakdownResponse> getItemBreakdown(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) @Size(max = 200) String region) {

        requireFeatureEnabled();
        AdvancedAnalyticsFilter filter = new AdvancedAnalyticsFilter(dateFrom, dateTo, region);
        ItemBreakdownResponse response = advancedAnalyticsService.getItemBreakdown(filter);
        return ResponseEntity.ok(response);
    }

    /**
     * Returns the segment-level redemption breakdown for the authenticated tenant (FR-08.2).
     *
     * <p>Each row represents one unique (region × role × currency) combination with at least
     * one redemption.  Both {@code region} and {@code role} are nullable (partner with no
     * location / user with no client role).
     *
     * <p>Optional {@code region} and {@code role} filters are combined with AND semantics.
     * Date range is capped at 365 days; missing dates default to last 30 days / today.
     * Response is Redis-cached 60 seconds keyed on tenant + filter combination (AC-2).
     *
     * @param dateFrom inclusive start date (ISO 8601; defaults to today − 30 days)
     * @param dateTo   inclusive end date   (ISO 8601; defaults to today)
     * @param region   optional region filter; null means all regions
     * @param role     optional role filter; null means all roles
     * @return 200 with {@link SegmentBreakdownResponse}; 422 if span exceeds 365 days; 403 if no permission
     */
    @GetMapping("/segment-breakdown")
    @RequiresPermission("action.redemption.analytics.advanced")
    @Operation(
            summary = "Redemption breakdown by partner segment",
            description = "Returns redemption metrics grouped by (region × role × currency). "
                    + "Zero-count combinations are omitted. Optional region and role filters apply AND semantics. "
                    + "Redis-cached 60 s per tenant + filter combination. "
                    + "Returns 422 when date span exceeds 365 days; 403 when permission or flag absent."
    )
    public ResponseEntity<SegmentBreakdownResponse> getSegmentBreakdown(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) @Size(max = 200) String region,
            @RequestParam(required = false) @Size(max = 200) String role) {

        requireFeatureEnabled();
        AdvancedAnalyticsFilter filter = new AdvancedAnalyticsFilter(dateFrom, dateTo, region, role);
        SegmentBreakdownResponse response = advancedAnalyticsService.getSegmentBreakdown(filter);
        return ResponseEntity.ok(response);
    }

    /**
     * Returns the time-to-first-redemption breakdown by region for the authenticated tenant
     * (FR-08.3).
     *
     * <p>Each row represents one region; a region with no completed redemptions in the cohort
     * window has {@code sampleCount = 0} and null {@code avgHoursToFirstRedemption} /
     * {@code medianHoursToFirstRedemption} (FE renders "N/A" for null cells — AC-2).
     *
     * <p>Date range is capped at 365 days; missing dates default to last 30 days / today.
     * An optional {@code region} filter constrains results to a single region.
     * Response is Redis-cached 60 seconds keyed on tenant + filter combination (AC-3).
     *
     * @param dateFrom inclusive start date (ISO 8601; defaults to today − 30 days)
     * @param dateTo   inclusive end date   (ISO 8601; defaults to today)
     * @param region   optional region filter; null means all regions
     * @return 200 with {@link TimeToFirstRedemptionResponse}; 422 if span exceeds 365 days;
     *         403 if no permission or feature flag disabled
     */
    @GetMapping("/time-to-first-redemption")
    @RequiresPermission("action.redemption.analytics.advanced")
    @Operation(
            summary = "Average time-to-first-redemption by region",
            description = "Returns mean hours from partner account creation to first COMPLETED "
                    + "RedemptionRequest, segmented by region. A region with sampleCount=0 returns "
                    + "null avg and median. Optional region filter. Redis-cached 60 s per tenant + "
                    + "filter combination. Returns 422 when date span exceeds 365 days; 403 when "
                    + "permission or flag absent."
    )
    public ResponseEntity<TimeToFirstRedemptionResponse> getTimeToFirstRedemption(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) @Size(max = 200) String region) {

        requireFeatureEnabled();
        AdvancedAnalyticsFilter filter = new AdvancedAnalyticsFilter(dateFrom, dateTo, region);
        TimeToFirstRedemptionResponse response = advancedAnalyticsService.getTimeToFirstRedemption(filter);
        return ResponseEntity.ok(response);
    }

    /**
     * Returns the redemption rate trend time series for the authenticated tenant (FR-08.4).
     *
     * <p>One data point per calendar day per currency type, within the selected date window.
     * Data points are ordered by {@code periodDate ASC} (AC-1).
     *
     * <p>Trend is tenant-wide — no region or role filter applies (spec FR-08.4 + Out-of-scope).
     * Date range is capped at 365 days; missing dates default to last 30 days / today.
     * Response is Redis-cached 60 seconds keyed on tenant + date range (AC-3).
     *
     * @param dateFrom inclusive start date (ISO 8601; defaults to today − 30 days)
     * @param dateTo   inclusive end date   (ISO 8601; defaults to today)
     * @return 200 with {@link RedemptionTrendResponse}; 422 if span exceeds 365 days;
     *         403 if no permission or feature flag disabled
     */
    @GetMapping("/trend")
    @RequiresPermission("action.redemption.analytics.advanced")
    @Operation(
            summary = "Redemption rate trend over time",
            description = "Returns a redemption-rate time series with one data point per calendar day "
                    + "per currency type within the selected window. Trend is tenant-wide (no region/role "
                    + "filter). Data points ordered by periodDate ASC. Redis-cached 60 s per tenant + "
                    + "date range. Returns 422 when date span exceeds 365 days; 403 when permission or "
                    + "flag absent."
    )
    public ResponseEntity<RedemptionTrendResponse> getRedemptionTrend(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {

        requireFeatureEnabled();
        RedemptionTrendResponse response = advancedAnalyticsService.getRedemptionTrend(dateFrom, dateTo);
        return ResponseEntity.ok(response);
    }

    /**
     * Returns the unredeemed balance liability trend time series for the authenticated tenant
     * (FR-08.5).
     *
     * <p>One data point per period-end date per currency type, within the selected date window.
     * Data points are ordered by {@code periodDate ASC} (AC-1).
     *
     * <p>Liability trend is tenant-wide — no region or role filter.
     * Date range is capped at 365 days; missing dates default to last 30 days / today.
     * Response is Redis-cached 60 seconds keyed on tenant + date range (spec caching table).
     *
     * @param dateFrom inclusive start date (ISO 8601; defaults to today − 30 days)
     * @param dateTo   inclusive end date   (ISO 8601; defaults to today)
     * @return 200 with {@link LiabilityTrendResponse}; 422 if span exceeds 365 days;
     *         403 if no permission or feature flag disabled
     */
    @GetMapping("/liability-trend")
    @RequiresPermission("action.redemption.analytics.advanced")
    @Operation(
            summary = "Unredeemed balance liability trend",
            description = "Returns the unredeemed balance liability (available + reserved) at each "
                    + "period-end data point within the selected window, per currency type. "
                    + "Data points ordered by periodDate ASC. Redis-cached 60 s per tenant + date range. "
                    + "Returns 422 when date span exceeds 365 days; 403 when permission or flag absent."
    )
    public ResponseEntity<LiabilityTrendResponse> getLiabilityTrend(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {

        requireFeatureEnabled();
        LiabilityTrendResponse response = advancedAnalyticsService.getLiabilityTrend(dateFrom, dateTo);
        return ResponseEntity.ok(response);
    }

    /**
     * Exports the unredeemed balance liability trend for the authenticated tenant as a CSV
     * attachment (FR-08.5, FR-08.10).
     *
     * <p>Rate limited to {@value AnalyticsExportRateLimiter#MAX_REQUESTS_PER_WINDOW} requests per
     * tenant per {@value AnalyticsExportRateLimiter#WINDOW_SECONDS} seconds via the shared
     * {@code AnalyticsExportRateLimiter}.  A 429 response includes a {@code Retry-After} header
     * with the seconds until the window resets (AC-4).
     *
     * <p>Not cached — always provides a live snapshot from {@code mv_liability_trend}.
     *
     * <p>Every successful 200 response emits an audit record:
     * {@code action=DATA_EXPORTED}, {@code resourceType=REDEMPTION_ADVANCED_ANALYTICS_EXPORT}
     * — written AFTER the CSV bytes are built so 422/429/403 paths produce no audit entry
     * (AC-3, BE-4, spec § Audit Trail).
     *
     * <p>No {@code @Audited} annotation — annotation fires unconditionally before any service
     * logic and cannot distinguish between success and error paths.
     *
     * @param dateFrom inclusive start date (ISO 8601; defaults to today − 30 days)
     * @param dateTo   inclusive end date   (ISO 8601; defaults to today)
     * @param request  current HTTP request (for rate-limit 429 error response path)
     * @return 200 with CSV attachment; 422 if span exceeds 365 days;
     *         429 if rate limit exceeded; 403 if no permission or flag disabled
     */
    @GetMapping("/liability-trend/export")
    @RequiresPermission("action.redemption.analytics.advanced")
    @Operation(
            summary = "Export liability trend as CSV",
            description = "CLIENT_ADMIN downloads the unredeemed balance liability trend as a CSV attachment. "
                    + "Columns: period_date, currency_type, total_unredeemed_balance. "
                    + "Rate limited to 3 req/min per tenant; 429 includes Retry-After header. "
                    + "Every download is audited (action=DATA_EXPORTED, "
                    + "resourceType=REDEMPTION_ADVANCED_ANALYTICS_EXPORT) — written after CSV built, "
                    + "so 422/429/403 paths produce no audit entry."
    )
    public ResponseEntity<Object> exportLiabilityTrend(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            HttpServletRequest request) {

        requireFeatureEnabled();
        UUID clientId = tenantValidator.getCurrentClientId();

        RateLimitResult rateLimit = exportRateLimiter.tryAcquireWithRetryAfter(clientId);
        if (!rateLimit.allowed()) {
            log.warn("step=advanced_analytics_rate_limit_exceeded " +
                            "tenantId={} endpoint=/api/v1/redemption/analytics/advanced/liability-trend/export",
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

        byte[] csv = advancedAnalyticsService.exportLiabilityTrend(dateFrom, dateTo);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("redemption-liability-trend.csv")
                .build());

        return ResponseEntity.ok().headers(headers).body(csv);
    }

    /**
     * Returns the failed and cancelled redemption breakdown by processing mode and catalog item
     * for the authenticated tenant (FR-08.7).
     *
     * <p>Rows are ordered by {@code failureRate} descending (DB-ordered; AC-1).
     * Each row shows {@code failedCount}, {@code cancelledCount}, {@code totalCount},
     * and {@code failureRate} (percentage 0–100) for a given processing mode × catalog item
     * × currency combination.
     *
     * <p>An optional {@code region} filter constrains results to redemptions by partners in the
     * specified region (FR-08.6, AC-3).
     *
     * <p>Date range is capped at 365 days; missing dates default to last 30 days / today.
     * Response is Redis-cached 60 seconds keyed on tenant + filter combination (AC-3).
     *
     * @param dateFrom inclusive start date (ISO 8601; defaults to today − 30 days)
     * @param dateTo   inclusive end date   (ISO 8601; defaults to today)
     * @param region   optional region filter; null means all regions
     * @return 200 with {@link FailureBreakdownResponse}; 422 if span exceeds 365 days;
     *         403 if no permission or feature flag disabled
     */
    @GetMapping("/failure-breakdown")
    @RequiresPermission("action.redemption.analytics.advanced")
    @Operation(
            summary = "Failure breakdown by processing mode and catalog item",
            description = "Returns failed and cancelled redemption metrics grouped by processing mode, "
                    + "catalog item, and currency, ordered by failureRate descending. "
                    + "processingMode values: INSTANT, BATCH, APPROVAL_REQUIRED. "
                    + "Optional region filter constrains to a single region. "
                    + "Redis-cached 60 s per tenant + filter combination. "
                    + "Returns 422 when date span exceeds 365 days; 403 when permission or flag absent."
    )
    public ResponseEntity<FailureBreakdownResponse> getFailureBreakdown(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) @Size(max = 200) String region) {

        requireFeatureEnabled();
        AdvancedAnalyticsFilter filter = new AdvancedAnalyticsFilter(dateFrom, dateTo, region);
        FailureBreakdownResponse response = advancedAnalyticsService.getFailureBreakdown(filter);
        return ResponseEntity.ok(response);
    }

    /**
     * Returns the timestamp of the most recent successful materialized view refresh and a
     * staleness flag (FR-08.8 + FR-08.11).
     *
     * <p>{@code isStale = true} when the data is older than 4 hours, or when
     * {@code lastRefreshedAt} is {@code null} (no refresh has run yet on first deploy).
     *
     * <p>Not Redis-cached — must reflect live {@code analytics_mv_refresh_log} state.
     */
    @GetMapping("/refresh-status")
    @RequiresPermission("action.redemption.analytics.advanced")
    @Operation(
            summary = "Materialized view refresh status",
            description = "Returns the most recent MV refresh timestamp and staleness indicator. "
                    + "isStale=true when data is older than 4 hours or lastRefreshedAt is null. "
                    + "Not cached — reflects live analytics_mv_refresh_log state. "
                    + "Returns 403 when permission or feature flag is absent."
    )
    public ResponseEntity<AnalyticsRefreshStatusResponse> getRefreshStatus() {
        requireFeatureEnabled();
        AnalyticsRefreshStatusResponse response = advancedAnalyticsService.getRefreshStatus();
        return ResponseEntity.ok(response);
    }

    /**
     * Controller-level feature-flag gate — runs on every request BEFORE the service
     * (and therefore before any Spring Cache lookup).  Prevents cached data from being
     * served to a tenant whose {@code redemption_analytics_advanced} flag was disabled
     * after a cache entry was populated.
     *
     * <p>Throws the same {@link AccessDeniedException} the service throws so that
     * {@code GlobalExceptionHandler} maps it to the same HTTP 403 shape.
     * The service-layer guard is kept as defence-in-depth for non-cached paths
     * (export, refresh-status).
     */
    private void requireFeatureEnabled() {
        UUID clientId = tenantValidator.getCurrentClientId();
        if (!featureFlagService.getEnabledFeatures(clientId).contains(FEATURE_FLAG_KEY)) {
            log.warn("step=advanced_analytics_feature_disabled tenantId={} featureFlag={} layer=controller",
                    clientId, FEATURE_FLAG_KEY);
            throw new AccessDeniedException(
                    "Advanced analytics is not available for your account.");
        }
    }
}
