package com.tenxengage.app.service.redemption;

import com.tenxengage.app.dto.request.redemption.AdvancedAnalyticsFilter;
import com.tenxengage.app.dto.response.redemption.AnalyticsRefreshStatusResponse;
import com.tenxengage.app.dto.response.redemption.DateWindowDto;
import com.tenxengage.app.dto.response.redemption.FailureBreakdownResponse;
import com.tenxengage.app.dto.response.redemption.FailureModeDto;
import com.tenxengage.app.dto.response.redemption.ItemBreakdownResponse;
import com.tenxengage.app.dto.response.redemption.ItemRedemptionDto;
import com.tenxengage.app.dto.response.redemption.LiabilityDataPointDto;
import com.tenxengage.app.dto.response.redemption.LiabilityTrendResponse;
import com.tenxengage.app.dto.response.redemption.RedemptionTrendResponse;
import com.tenxengage.app.dto.response.redemption.RegionTimeToRedemptionDto;
import com.tenxengage.app.dto.response.redemption.SegmentBreakdownResponse;
import com.tenxengage.app.dto.response.redemption.SegmentRedemptionDto;
import com.tenxengage.app.dto.response.redemption.TimeToFirstRedemptionResponse;
import com.tenxengage.app.dto.response.redemption.TrendDataPointDto;
import com.tenxengage.app.entity.enums.AuditAction;
import com.tenxengage.app.entity.enums.AuditResourceType;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.AuditLogService;
import com.tenxengage.app.service.FeatureFlagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.namedparam.EmptySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.lang.Nullable;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Service for Advanced Redemption Analytics (FR-08.x).
 *
 * <p>All methods are read-only.  Every method opens with a feature-flag gate:
 * if {@code redemption_analytics_advanced} is not enabled for the current tenant,
 * an {@link AccessDeniedException} is thrown so the controller returns HTTP 403.
 *
 * <p>MV queries are issued as native SQL via {@link NamedParameterJdbcTemplate} —
 * no JPA entities exist for materialized views.
 */
@Service
@Transactional(readOnly = true)
public class RedemptionAdvancedAnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(RedemptionAdvancedAnalyticsService.class);

    private static final String FEATURE_FLAG_KEY = "redemption_analytics_advanced";
    private static final int STALENESS_THRESHOLD_HOURS = 4;

    /**
     * Number of materialized views that must appear in {@code analytics_mv_refresh_log}
     * for the endpoint to consider the data potentially fresh.  Must stay in sync with
     * {@link AnalyticsMvRefreshScheduler#MV_NAMES}.
     */
    private static final int EXPECTED_MV_COUNT = AnalyticsMvRefreshScheduler.MV_NAMES.size();

    /**
     * Selects the minimum {@code last_refreshed_at} and the row count across all rows in
     * {@code analytics_mv_refresh_log}.  Taking the minimum means the staleness check
     * uses the oldest successfully-refreshed MV, which is the binding constraint for
     * the UI banner (FR-08.11).  The row count is compared against {@link #EXPECTED_MV_COUNT}
     * so that a partially-failed first refresh (missing rows) is also treated as stale.
     */
    private static final String REFRESH_STATUS_QUERY =
            "SELECT MIN(last_refreshed_at) AS min_last_refreshed_at, " +
            "       COUNT(*)              AS mv_count " +
            "FROM analytics_mv_refresh_log";

    /** Pre-compiled to avoid per-request Pattern compilation cost in logging sanitisation. */
    private static final Pattern CRLF_PATTERN = Pattern.compile("[\r\n]");

    private final NamedParameterJdbcTemplate namedJdbc;
    private final TenantValidator tenantValidator;
    private final FeatureFlagService featureFlagService;
    private final AuditLogService auditLogService;

    public RedemptionAdvancedAnalyticsService(NamedParameterJdbcTemplate namedJdbc,
                                              TenantValidator tenantValidator,
                                              FeatureFlagService featureFlagService,
                                              AuditLogService auditLogService) {
        this.namedJdbc = namedJdbc;
        this.tenantValidator = tenantValidator;
        this.featureFlagService = featureFlagService;
        this.auditLogService = auditLogService;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Returns the MV refresh status.
     *
     * <p>Queries {@code analytics_mv_refresh_log} for the minimum
     * {@code last_refreshed_at} across all MV rows.  If the table is empty (first
     * deploy, scheduler hasn't run yet) {@code lastRefreshedAt} is {@code null} and
     * {@code isStale} is {@code true}.
     *
     * <p>Not cached — must reflect live log-table state (spec NFR).
     *
     * @return refresh status DTO
     * @throws AccessDeniedException when {@code redemption_analytics_advanced} flag is disabled
     */
    public AnalyticsRefreshStatusResponse getRefreshStatus() {
        UUID clientId = tenantValidator.getCurrentClientId();
        requireFeatureEnabled(clientId);

        RefreshLogRow row = queryRefreshLogRow();

        // Treat missing rows as stale: if fewer than EXPECTED_MV_COUNT rows exist,
        // at least one MV has never successfully refreshed (partial-failure / first deploy).
        boolean missingRows = row.mvCount() < EXPECTED_MV_COUNT;
        Instant minLastRefreshedAt = missingRows ? null : row.minLastRefreshedAt();

        if (minLastRefreshedAt == null) {
            log.warn("step=advanced_analytics_stale_data_served tenantId={} lastRefreshedAt=null " +
                            "staleForHours=N/A mvCount={} expectedMvCount={}",
                    clientId, row.mvCount(), EXPECTED_MV_COUNT);
        } else {
            Instant now = Instant.now();
            boolean stale = minLastRefreshedAt.isBefore(
                    now.minusSeconds((long) STALENESS_THRESHOLD_HOURS * 3600));
            if (stale) {
                long staleForHours = Duration.between(minLastRefreshedAt, now).toHours();
                log.warn("step=advanced_analytics_stale_data_served tenantId={} lastRefreshedAt={} staleForHours={}",
                        clientId, minLastRefreshedAt, staleForHours);
            }
        }

        return AnalyticsRefreshStatusResponse.of(minLastRefreshedAt, STALENESS_THRESHOLD_HOURS);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Item breakdown (FR-08.1)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Returns a cache key for the item-breakdown endpoint, scoped to the current tenant
     * and all filter parameters.  Called via SpEL
     * {@code #root.target.buildAdvancedCacheKey(#filter)} so that different tenants and
     * filter combinations never share a cached response.
     *
     * @param filter the current request filter
     * @return cache key in the shape {@code {clientId}:item-breakdown:{dateFrom}:{dateTo}:{region}}
     */
    public String buildAdvancedCacheKey(AdvancedAnalyticsFilter filter) {
        UUID clientId = tenantValidator.getCurrentClientId();
        String region = filter.region() != null ? filter.region() : "";
        return "%s:item-breakdown:%s:%s:%s".formatted(clientId, filter.dateFrom(), filter.dateTo(), region);
    }

    /**
     * Returns the item-level redemption breakdown for the authenticated tenant (FR-08.1).
     *
     * <p>Validates that:
     * <ul>
     *   <li>{@code dateFrom ≤ dateTo} — throws {@link BusinessRuleException} (422)</li>
     *   <li>Span ≤ 365 days — throws {@link BusinessRuleException} (422)</li>
     * </ul>
     *
     * <p>Applies defaults for missing dates: {@code dateFrom} = today − 30 days,
     * {@code dateTo} = today.
     *
     * <p>Queries {@code mv_item_redemption_breakdown} via {@link NamedParameterJdbcTemplate},
     * binds {@code client_id} and the date range, and optionally appends a region predicate.
     * Results are ordered by {@code total_redeemed_count DESC} by the DB query.
     *
     * <p>Response is Redis-cached 60 seconds keyed
     * {@code {clientId}:item-breakdown:{dateFrom}:{dateTo}:{region}}.
     *
     * @param filter the query filter (dates + optional region)
     * @return item breakdown response
     * @throws AccessDeniedException when the feature flag is disabled
     * @throws BusinessRuleException when date range constraints are violated
     */
    @Cacheable(value = "advanced-analytics-item-breakdown", key = "#root.target.buildAdvancedCacheKey(#filter)")
    public ItemBreakdownResponse getItemBreakdown(AdvancedAnalyticsFilter filter) {
        UUID clientId = tenantValidator.getCurrentClientId();
        requireFeatureEnabled(clientId);

        LocalDate dateFrom = filter.dateFrom() != null ? filter.dateFrom()
                : LocalDate.now(ZoneOffset.UTC).minusDays(30);
        LocalDate dateTo = filter.dateTo() != null ? filter.dateTo()
                : LocalDate.now(ZoneOffset.UTC);

        validateDateRange(dateFrom, dateTo);

        long start = System.nanoTime();
        List<ItemRedemptionDto> items = queryItemBreakdown(clientId, dateFrom, dateTo, filter.region());
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        String safeRegion = filter.region() != null ? CRLF_PATTERN.matcher(filter.region()).replaceAll("_") : null;
        log.info("step=advanced_analytics_query clientId={} endpoint=item-breakdown " +
                        "dateFrom={} dateTo={} region={} durationMs={}",
                clientId, dateFrom, dateTo, safeRegion, durationMs);

        Instant lastRefreshedAt = queryMinLastRefreshedAt();
        if (lastRefreshedAt == null) {
            log.warn("step=advanced_analytics_no_refresh_data tenantId={} endpoint=item-breakdown", clientId);
        }
        return new ItemBreakdownResponse(DateWindowDto.of(dateFrom, dateTo), items, lastRefreshedAt);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Segment breakdown (FR-08.2)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Returns a cache key for the segment-breakdown endpoint, scoped to the current tenant
     * and all filter parameters.  Called via SpEL
     * {@code #root.target.buildSegmentCacheKey(#filter)}.
     *
     * @param filter the current request filter
     * @return cache key in the shape
     *         {@code {clientId}:segment-breakdown:{dateFrom}:{dateTo}:{region}:{role}}
     */
    public String buildSegmentCacheKey(AdvancedAnalyticsFilter filter) {
        UUID clientId = tenantValidator.getCurrentClientId();
        String region = filter.region() != null ? filter.region() : "";
        String role   = filter.role()   != null ? filter.role()   : "";
        return "%s:segment-breakdown:%s:%s:%s:%s"
                .formatted(clientId, filter.dateFrom(), filter.dateTo(), region, role);
    }

    /**
     * Returns the segment-level redemption breakdown for the authenticated tenant (FR-08.2).
     *
     * <p>Each row represents one unique (region × role × currency) combination.
     * Zero-count combinations are omitted by the MV query.
     *
     * <p>Validates that:
     * <ul>
     *   <li>{@code dateFrom ≤ dateTo} — throws {@link BusinessRuleException} (422)</li>
     *   <li>Span ≤ 365 days — throws {@link BusinessRuleException} (422)</li>
     * </ul>
     *
     * <p>Applies defaults for missing dates: {@code dateFrom} = today − 30 days,
     * {@code dateTo} = today.
     *
     * <p>Queries {@code mv_segment_redemption_breakdown} via {@link NamedParameterJdbcTemplate}.
     * Optional {@code region} and {@code role} predicates are appended when non-null.
     *
     * <p>Response is Redis-cached 60 seconds keyed
     * {@code {clientId}:segment-breakdown:{dateFrom}:{dateTo}:{region}:{role}}.
     *
     * @param filter the query filter (dates + optional region + optional role)
     * @return segment breakdown response
     * @throws org.springframework.security.access.AccessDeniedException when the feature flag is disabled
     * @throws BusinessRuleException when date range constraints are violated
     */
    @Cacheable(value = "advanced-analytics-segment-breakdown",
               key = "#root.target.buildSegmentCacheKey(#filter)")
    public SegmentBreakdownResponse getSegmentBreakdown(AdvancedAnalyticsFilter filter) {
        UUID clientId = tenantValidator.getCurrentClientId();
        requireFeatureEnabled(clientId);

        LocalDate dateFrom = filter.dateFrom() != null ? filter.dateFrom()
                : LocalDate.now(ZoneOffset.UTC).minusDays(30);
        LocalDate dateTo = filter.dateTo() != null ? filter.dateTo()
                : LocalDate.now(ZoneOffset.UTC);

        validateDateRange(dateFrom, dateTo);

        long start = System.nanoTime();
        List<SegmentRedemptionDto> segments =
                querySegmentBreakdown(clientId, dateFrom, dateTo, filter.region(), filter.role());
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        String safeRegion = filter.region() != null ? CRLF_PATTERN.matcher(filter.region()).replaceAll("_") : null;
        String safeRole   = filter.role()   != null ? CRLF_PATTERN.matcher(filter.role()).replaceAll("_")   : null;
        log.info("step=advanced_analytics_query clientId={} endpoint=segment-breakdown " +
                        "dateFrom={} dateTo={} region={} role={} durationMs={}",
                clientId, dateFrom, dateTo, safeRegion, safeRole, durationMs);

        Instant lastRefreshedAt = queryMinLastRefreshedAt();
        if (lastRefreshedAt == null) {
            log.warn("step=advanced_analytics_no_refresh_data tenantId={} endpoint=segment-breakdown",
                    clientId);
        }
        return new SegmentBreakdownResponse(DateWindowDto.of(dateFrom, dateTo), segments, lastRefreshedAt);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Time-to-first-redemption (FR-08.3)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Returns a cache key for the time-to-first-redemption endpoint, scoped to the current
     * tenant and all filter parameters.  Called via SpEL
     * {@code #root.target.buildTtfrCacheKey(#filter)}.
     *
     * @param filter the current request filter
     * @return cache key in the shape {@code {clientId}:ttfr:{dateFrom}:{dateTo}:{region}}
     */
    public String buildTtfrCacheKey(AdvancedAnalyticsFilter filter) {
        UUID clientId = tenantValidator.getCurrentClientId();
        String region = filter.region() != null ? filter.region() : "";
        return "%s:ttfr:%s:%s:%s".formatted(clientId, filter.dateFrom(), filter.dateTo(), region);
    }

    /**
     * Returns the time-to-first-redemption breakdown by region for the authenticated tenant
     * (FR-08.3).
     *
     * <p>Each row represents one region (the top-level location of the partner company).
     * A region with no completed redemptions in the cohort window is returned with
     * {@code sampleCount = 0} and {@code null} avg/median fields (AC-2).
     *
     * <p>Validates that:
     * <ul>
     *   <li>{@code dateFrom ≤ dateTo} — throws {@link BusinessRuleException} (422)</li>
     *   <li>Span ≤ 365 days — throws {@link BusinessRuleException} (422)</li>
     * </ul>
     *
     * <p>Applies defaults for missing dates: {@code dateFrom} = today − 30 days,
     * {@code dateTo} = today.
     *
     * <p>Queries {@code mv_time_to_first_redemption} via {@link NamedParameterJdbcTemplate},
     * grouping daily cohort rows into a single per-region summary using
     * {@code SUM(sum_hours_to_first_redemption) / SUM(sample_count)}.  The median field is
     * returned as {@code null} because the MV stores a per-daily-cohort median; re-computing
     * the true global median from aggregated rows is not supported without the raw data.
     *
     * <p>Response is Redis-cached 60 seconds keyed
     * {@code {clientId}:ttfr:{dateFrom}:{dateTo}:{region}}.
     *
     * @param filter the query filter (dates + optional region)
     * @return TTFR response containing a per-region list and the MV refresh timestamp
     * @throws org.springframework.security.access.AccessDeniedException when the feature flag is disabled
     * @throws BusinessRuleException when date range constraints are violated
     */
    @Cacheable(value = "advanced-analytics-ttfr", key = "#root.target.buildTtfrCacheKey(#filter)")
    public TimeToFirstRedemptionResponse getTimeToFirstRedemption(AdvancedAnalyticsFilter filter) {
        UUID clientId = tenantValidator.getCurrentClientId();
        requireFeatureEnabled(clientId);

        LocalDate dateFrom = filter.dateFrom() != null ? filter.dateFrom()
                : LocalDate.now(ZoneOffset.UTC).minusDays(30);
        LocalDate dateTo = filter.dateTo() != null ? filter.dateTo()
                : LocalDate.now(ZoneOffset.UTC);

        validateDateRange(dateFrom, dateTo);

        long start = System.nanoTime();
        List<RegionTimeToRedemptionDto> regions =
                queryTimeToFirstRedemption(clientId, dateFrom, dateTo, filter.region());
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        String safeRegion = filter.region() != null
                ? CRLF_PATTERN.matcher(filter.region()).replaceAll("_") : null;
        log.info("step=advanced_analytics_query clientId={} endpoint=time-to-first-redemption " +
                        "dateFrom={} dateTo={} region={} durationMs={}",
                clientId, dateFrom, dateTo, safeRegion, durationMs);

        Instant lastRefreshedAt = queryMinLastRefreshedAt();
        if (lastRefreshedAt == null) {
            log.warn("step=advanced_analytics_no_refresh_data tenantId={} endpoint=time-to-first-redemption",
                    clientId);
        }

        // Build the active filters map for the response (AC-1: echoes applied filter values)
        Map<String, String> activeFilters = filter.region() != null && !filter.region().isBlank()
                ? Map.of("region", filter.region())
                : Map.of();

        return new TimeToFirstRedemptionResponse(activeFilters, regions, lastRefreshedAt);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Redemption rate trend (FR-08.4)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Returns a cache key for the redemption-rate trend endpoint, scoped to the current
     * tenant and the date range.  Called via SpEL
     * {@code #root.target.buildTrendCacheKey(#dateFrom, #dateTo)}.
     *
     * @param dateFrom inclusive start date
     * @param dateTo   inclusive end date
     * @return cache key in the shape {@code {clientId}:trend:{dateFrom}:{dateTo}}
     */
    public String buildTrendCacheKey(LocalDate dateFrom, LocalDate dateTo) {
        UUID clientId = tenantValidator.getCurrentClientId();
        return "%s:trend:%s:%s".formatted(clientId, dateFrom, dateTo);
    }

    /**
     * Returns the redemption rate trend time series for the authenticated tenant (FR-08.4).
     *
     * <p>Validates that:
     * <ul>
     *   <li>{@code dateFrom ≤ dateTo} — throws {@link BusinessRuleException} (422)</li>
     *   <li>Span ≤ 365 days — throws {@link BusinessRuleException} (422)</li>
     * </ul>
     *
     * <p>Applies defaults for missing dates: {@code dateFrom} = today − 30 days,
     * {@code dateTo} = today.
     *
     * <p>Queries {@code mv_redemption_rate_trend} via {@link NamedParameterJdbcTemplate},
     * binds {@code client_id} and the date range.  Results are ordered by
     * {@code period_date ASC, currency_type ASC} by the DB query (AC-1).
     *
     * <p>Trend is tenant-wide — no region or role filter (spec FR-08.4 + Out-of-scope note).
     *
     * <p>Response is Redis-cached 60 seconds keyed {@code {clientId}:trend:{dateFrom}:{dateTo}}
     * (AC-3).
     *
     * @param dateFrom inclusive start date (null → today − 30 days)
     * @param dateTo   inclusive end date   (null → today)
     * @return trend response with ordered data points and the MV refresh timestamp
     * @throws org.springframework.security.access.AccessDeniedException when the feature flag is disabled
     * @throws BusinessRuleException when date range constraints are violated
     */
    @Cacheable(value = "advanced-analytics-trend",
               key = "#root.target.buildTrendCacheKey(#dateFrom, #dateTo)")
    public RedemptionTrendResponse getRedemptionTrend(LocalDate dateFrom, LocalDate dateTo) {
        UUID clientId = tenantValidator.getCurrentClientId();
        requireFeatureEnabled(clientId);

        LocalDate resolvedFrom = dateFrom != null ? dateFrom : LocalDate.now(ZoneOffset.UTC).minusDays(30);
        LocalDate resolvedTo   = dateTo   != null ? dateTo   : LocalDate.now(ZoneOffset.UTC);

        validateDateRange(resolvedFrom, resolvedTo);

        long start = System.nanoTime();
        List<TrendDataPointDto> dataPoints = queryRedemptionTrend(clientId, resolvedFrom, resolvedTo);
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        log.info("step=advanced_analytics_query clientId={} endpoint=trend " +
                        "dateFrom={} dateTo={} durationMs={}",
                clientId, resolvedFrom, resolvedTo, durationMs);

        Instant lastRefreshedAt = queryMinLastRefreshedAt();
        if (lastRefreshedAt == null) {
            log.warn("step=advanced_analytics_no_refresh_data clientId={} endpoint=trend", clientId);
        }
        return new RedemptionTrendResponse(DateWindowDto.of(resolvedFrom, resolvedTo),
                dataPoints, lastRefreshedAt);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Liability trend (FR-08.5)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Returns a cache key for the liability-trend endpoint, scoped to the current tenant
     * and the date range.  Called via SpEL
     * {@code #root.target.buildLiabilityTrendCacheKey(#dateFrom, #dateTo)}.
     *
     * @param dateFrom inclusive start date
     * @param dateTo   inclusive end date
     * @return cache key in the shape {@code {clientId}:liability-trend:{dateFrom}:{dateTo}}
     */
    public String buildLiabilityTrendCacheKey(LocalDate dateFrom, LocalDate dateTo) {
        UUID clientId = tenantValidator.getCurrentClientId();
        return "%s:liability-trend:%s:%s".formatted(clientId, dateFrom, dateTo);
    }

    /**
     * Returns the unredeemed balance liability trend time series for the authenticated tenant
     * (FR-08.5).
     *
     * <p>Validates that:
     * <ul>
     *   <li>{@code dateFrom ≤ dateTo} — throws {@link BusinessRuleException} (422)</li>
     *   <li>Span ≤ 365 days — throws {@link BusinessRuleException} (422)</li>
     * </ul>
     *
     * <p>Applies defaults for missing dates: {@code dateFrom} = today − 30 days,
     * {@code dateTo} = today.
     *
     * <p>Queries {@code mv_liability_trend} via {@link NamedParameterJdbcTemplate},
     * binds {@code client_id} and the date range.  Results are ordered by
     * {@code period_date ASC, currency_type ASC} by the DB query (AC-1).
     *
     * <p>Response is Redis-cached 60 seconds keyed
     * {@code {clientId}:liability-trend:{dateFrom}:{dateTo}} (spec caching table).
     *
     * @param dateFrom inclusive start date (null → today − 30 days)
     * @param dateTo   inclusive end date   (null → today)
     * @return liability trend response with ordered data points and the MV refresh timestamp
     * @throws org.springframework.security.access.AccessDeniedException when the feature flag is disabled
     * @throws BusinessRuleException when date range constraints are violated
     */
    @Cacheable(value = "advanced-analytics-liability-trend",
               key = "#root.target.buildLiabilityTrendCacheKey(#dateFrom, #dateTo)")
    public LiabilityTrendResponse getLiabilityTrend(LocalDate dateFrom, LocalDate dateTo) {
        UUID clientId = tenantValidator.getCurrentClientId();
        requireFeatureEnabled(clientId);

        LocalDate resolvedFrom = dateFrom != null ? dateFrom : LocalDate.now(ZoneOffset.UTC).minusDays(30);
        LocalDate resolvedTo   = dateTo   != null ? dateTo   : LocalDate.now(ZoneOffset.UTC);

        validateDateRange(resolvedFrom, resolvedTo);

        long start = System.nanoTime();
        List<LiabilityDataPointDto> dataPoints = queryLiabilityTrend(clientId, resolvedFrom, resolvedTo);
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        log.info("step=advanced_analytics_query clientId={} endpoint=liability-trend " +
                        "dateFrom={} dateTo={} durationMs={}",
                clientId, resolvedFrom, resolvedTo, durationMs);

        Instant lastRefreshedAt = queryMinLastRefreshedAt();
        if (lastRefreshedAt == null) {
            log.warn("step=advanced_analytics_no_refresh_data clientId={} endpoint=liability-trend",
                    clientId);
        }
        return new LiabilityTrendResponse(DateWindowDto.of(resolvedFrom, resolvedTo),
                dataPoints, lastRefreshedAt);
    }

    /**
     * Exports the unredeemed balance liability trend for the authenticated tenant as a UTF-8
     * CSV byte array (FR-08.5, FR-08.10).
     *
     * <p>Not cached — always provides a live snapshot from {@code mv_liability_trend}.
     *
     * <p>Validates that:
     * <ul>
     *   <li>{@code dateFrom ≤ dateTo} — throws {@link BusinessRuleException} (422)</li>
     *   <li>Span ≤ 365 days — throws {@link BusinessRuleException} (422)</li>
     * </ul>
     *
     * <p>CSV columns: {@code period_date,currency_type,total_unredeemed_balance}.
     * All string fields are processed through {@link #escapeCsv(String)} to neutralise
     * formula-injection attacks (CWE-1236).
     *
     * <p>The audit log entry is written via {@link AuditLogService#logAsync} AFTER the CSV
     * bytes are built, so that a failure during CSV construction (e.g. DB error) does not
     * produce an audit entry.  This matches the pattern in F-07's
     * {@code RedemptionAnalyticsService.exportUnredeemedBalances()}.
     *
     * <p>No {@code @Audited} annotation — the annotation fires before any service logic,
     * so it cannot distinguish 422/429/403 from 200.  The manual call below only runs when
     * the CSV has been successfully built (AC-3, BE-4).
     *
     * @param dateFrom inclusive start date (null → today − 30 days)
     * @param dateTo   inclusive end date   (null → today)
     * @return UTF-8 encoded CSV bytes; always includes the header row
     * @throws org.springframework.security.access.AccessDeniedException when the feature flag is disabled
     * @throws BusinessRuleException when date range constraints are violated
     */
    public byte[] exportLiabilityTrend(LocalDate dateFrom, LocalDate dateTo) {
        UUID clientId = tenantValidator.getCurrentClientId();
        UUID userId   = tenantValidator.getCurrentUserId();
        requireFeatureEnabled(clientId);

        LocalDate resolvedFrom = dateFrom != null ? dateFrom : LocalDate.now(ZoneOffset.UTC).minusDays(30);
        LocalDate resolvedTo   = dateTo   != null ? dateTo   : LocalDate.now(ZoneOffset.UTC);

        validateDateRange(resolvedFrom, resolvedTo);

        List<LiabilityDataPointDto> dataPoints = queryLiabilityTrend(clientId, resolvedFrom, resolvedTo);

        // Build the CSV — header always present, then one row per data point.
        // All string fields pass through escapeCsv() for uniform formula-injection defence
        // (CWE-1236).  periodDate produces ISO-8601 output from LocalDate.toString() which is
        // safe today, but routing through escapeCsv() keeps the defence consistent and guards
        // against future MV schema changes that could surface non-ISO values.
        StringBuilder csv = new StringBuilder();
        csv.append("period_date,currency_type,total_unredeemed_balance\n");
        for (LiabilityDataPointDto dp : dataPoints) {
            csv.append(escapeCsv(dp.periodDate() != null ? dp.periodDate().toString() : "")).append(',')
               .append(escapeCsv(dp.currencyId())).append(',')
               .append(dp.totalUnredeemedBalance() != null ? dp.totalUnredeemedBalance().toPlainString() : "")
               .append('\n');
        }

        byte[] result = csv.toString().getBytes(StandardCharsets.UTF_8);

        // Log observability event after CSV is built (spec § Observability).
        log.info("step=advanced_analytics_export_downloaded tenantId={} userId={} " +
                        "dateFrom={} dateTo={} rowCount={}",
                clientId, userId, resolvedFrom, resolvedTo, dataPoints.size());

        // Write audit log AFTER CSV bytes built — 422/429/403 paths never reach this line
        // (AC-3, BE-4, spec § Audit Trail).
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("tenantId", clientId.toString());
        metadata.put("userId", userId.toString());
        metadata.put("dateFrom", resolvedFrom.toString());
        metadata.put("dateTo", resolvedTo.toString());
        metadata.put("rowCount", dataPoints.size());

        auditLogService.logAsync(
                AuditAction.DATA_EXPORTED,
                AuditResourceType.REDEMPTION_ADVANCED_ANALYTICS_EXPORT,
                null,
                "liability-trend-export",
                "LIABILITY_TREND_CSV_EXPORTED",
                metadata
        );

        return result;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Failure breakdown (FR-08.7)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Returns a cache key for the failure-breakdown endpoint, scoped to the current tenant
     * and all filter parameters.  Called via SpEL
     * {@code #root.target.buildFailureBreakdownCacheKey(#filter)}.
     *
     * @param filter the current request filter
     * @return cache key in the shape {@code {clientId}:failure-breakdown:{dateFrom}:{dateTo}:{region}}
     */
    public String buildFailureBreakdownCacheKey(AdvancedAnalyticsFilter filter) {
        UUID clientId = tenantValidator.getCurrentClientId();
        String region = filter.region() != null ? filter.region() : "";
        return "%s:failure-breakdown:%s:%s:%s".formatted(clientId, filter.dateFrom(), filter.dateTo(), region);
    }

    /**
     * Returns the failure mode breakdown for the authenticated tenant (FR-08.7).
     *
     * <p>Each row represents one unique (processing mode × catalog item × currency) combination.
     * Rows are ordered by {@code failure_rate DESC} at the DB level (AC-1).
     *
     * <p>Validates that:
     * <ul>
     *   <li>{@code dateFrom ≤ dateTo} — throws {@link BusinessRuleException} (422)</li>
     *   <li>Span ≤ 365 days — throws {@link BusinessRuleException} (422)</li>
     * </ul>
     *
     * <p>Applies defaults for missing dates: {@code dateFrom} = today − 30 days,
     * {@code dateTo} = today.
     *
     * <p>Queries {@code mv_failure_mode_breakdown} via {@link NamedParameterJdbcTemplate},
     * binds {@code client_id} and the date range.  An optional {@code region} predicate is
     * appended when non-null.
     *
     * <p>Response is Redis-cached 60 seconds keyed
     * {@code {clientId}:failure-breakdown:{dateFrom}:{dateTo}:{region}} (AC-3).
     *
     * @param filter the query filter (dates + optional region)
     * @return failure breakdown response with rows sorted by failureRate desc and MV refresh timestamp
     * @throws org.springframework.security.access.AccessDeniedException when the feature flag is disabled
     * @throws BusinessRuleException when date range constraints are violated
     */
    @Cacheable(value = "advanced-analytics-failure-breakdown",
               key = "#root.target.buildFailureBreakdownCacheKey(#filter)")
    public FailureBreakdownResponse getFailureBreakdown(AdvancedAnalyticsFilter filter) {
        UUID clientId = tenantValidator.getCurrentClientId();
        requireFeatureEnabled(clientId);

        LocalDate dateFrom = filter.dateFrom() != null ? filter.dateFrom()
                : LocalDate.now(ZoneOffset.UTC).minusDays(30);
        LocalDate dateTo = filter.dateTo() != null ? filter.dateTo()
                : LocalDate.now(ZoneOffset.UTC);

        validateDateRange(dateFrom, dateTo);

        long start = System.nanoTime();
        List<FailureModeDto> failureModes = queryFailureBreakdown(clientId, dateFrom, dateTo, filter.region());
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        String safeRegion = filter.region() != null ? CRLF_PATTERN.matcher(filter.region()).replaceAll("_") : null;
        log.info("step=advanced_analytics_query clientId={} endpoint=failure-breakdown " +
                        "dateFrom={} dateTo={} region={} durationMs={}",
                clientId, dateFrom, dateTo, safeRegion, durationMs);

        Instant lastRefreshedAt = queryMinLastRefreshedAt();
        if (lastRefreshedAt == null) {
            log.warn("step=advanced_analytics_no_refresh_data tenantId={} endpoint=failure-breakdown", clientId);
        }
        return new FailureBreakdownResponse(DateWindowDto.of(dateFrom, dateTo), failureModes, lastRefreshedAt);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Executes the time-to-first-redemption native SQL against
     * {@code mv_time_to_first_redemption}.
     *
     * <p>The MV stores one row per (client_id, region, first_redemption_date) cohort, carrying
     * pre-computed {@code sum_hours_to_first_redemption} and {@code sample_count} values that
     * let the query aggregate daily cohort rows into a single per-region summary without
     * materializing the underlying raw durations.
     *
     * <p>The {@code avg_hours_to_first_redemption} column in the MV is a per-daily-cohort
     * average; re-averaging it directly would produce a day-count-weighted average rather than
     * a sample-count-weighted average.  Instead the query recomputes avg as
     * {@code SUM(sum_hours) / SUM(sample_count)} — mathematically equivalent to the
     * sample-count-weighted mean.
     *
     * <p>The median is returned as {@code null}: the MV stores a per-daily-cohort median;
     * a true global median cannot be derived from aggregated cohort medians without the raw
     * durations.  This matches the technical.md query template which explicitly sets the
     * median alias to {@code NULL}.
     *
     * <p>When {@code SUM(sample_count) = 0} the {@code CASE} expression returns {@code NULL}
     * for avg, matching AC-2 (FE renders "N/A").
     *
     * @param clientId  current tenant
     * @param dateFrom  inclusive start of the cohort window (partner's first redemption date)
     * @param dateTo    inclusive end of the cohort window
     * @param region    optional region filter; {@code null} means all regions
     * @return per-region TTFR rows, ordered by region
     */
    private List<RegionTimeToRedemptionDto> queryTimeToFirstRedemption(UUID clientId,
                                                                        LocalDate dateFrom,
                                                                        LocalDate dateTo,
                                                                        String region) {
        StringBuilder sql = new StringBuilder(
                "SELECT region, " +
                "       CASE WHEN SUM(sample_count) = 0 THEN NULL " +
                "            ELSE SUM(sum_hours_to_first_redemption) " +
                "                 / NULLIF(SUM(sample_count), 0) " +
                "       END  AS avg_hours_to_first_redemption, " +
                "       NULL AS median_hours_to_first_redemption, " +
                "       SUM(sample_count) AS sample_count " +
                "FROM mv_time_to_first_redemption " +
                "WHERE client_id              = :clientId " +
                "  AND first_redemption_date BETWEEN :dateFrom AND :dateTo"
        );

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("clientId", clientId)
                .addValue("dateFrom", dateFrom)
                .addValue("dateTo",   dateTo);

        List<String> regions = parseMultiValue(region);
        if (!regions.isEmpty()) {
            sql.append(" AND region IN (:regions)");
            params.addValue("regions", regions);
        }

        sql.append(" GROUP BY region ORDER BY region");

        return namedJdbc.query(sql.toString(), params, (rs, rowNum) -> {
            BigDecimal avg    = rs.getBigDecimal("avg_hours_to_first_redemption");
            // median is always NULL from the query (re-aggregating cohort medians is unsound)
            BigDecimal median = rs.getBigDecimal("median_hours_to_first_redemption");
            long sampleCount  = rs.getLong("sample_count");
            return new RegionTimeToRedemptionDto(
                    rs.getString("region"),
                    avg,
                    median,
                    sampleCount
            );
        });
    }

    /**
     * Executes the redemption rate trend native SQL against {@code mv_redemption_rate_trend}.
     *
     * <p>Results are ordered by {@code period_date ASC, currency_type ASC} at the DB level,
     * guaranteeing AC-1's "ordered by periodDate ASC" contract without in-memory sorting.
     *
     * <p>The MV column {@code currency_type} is aliased as {@code currency_id} to match the
     * contract field name ({@code currencyId}) in {@link TrendDataPointDto}.
     *
     * <p>{@code redemption_rate} in the MV is stored as a percentage (0–100, NUMERIC with 2dp);
     * it is read directly as a {@code double} — no conversion needed (wire format is 0–100).
     *
     * @param clientId  current tenant
     * @param dateFrom  inclusive start of the date window
     * @param dateTo    inclusive end of the date window
     * @return list of trend data points, ordered period_date ASC, currency_type ASC
     */
    private List<TrendDataPointDto> queryRedemptionTrend(UUID clientId,
                                                          LocalDate dateFrom,
                                                          LocalDate dateTo) {
        String sql =
                "SELECT period_date, currency_type AS currency_id, " +
                "       redeemed_count, redemption_rate " +
                "FROM mv_redemption_rate_trend " +
                "WHERE client_id  = :clientId " +
                "  AND period_date BETWEEN :dateFrom AND :dateTo " +
                "ORDER BY period_date ASC, currency_type ASC";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("clientId", clientId)
                .addValue("dateFrom", dateFrom)
                .addValue("dateTo",   dateTo);

        return namedJdbc.query(sql, params, (rs, rowNum) -> {
            java.sql.Date sqlDate = rs.getDate("period_date");
            LocalDate periodDate  = sqlDate != null ? sqlDate.toLocalDate() : null;
            return new TrendDataPointDto(
                    periodDate,
                    rs.getString("currency_id"),
                    rs.getLong("redeemed_count"),
                    rs.getDouble("redemption_rate")
            );
        });
    }

    /**
     * Executes the failure breakdown native SQL against {@code mv_failure_mode_breakdown}.
     *
     * <p>Aggregates daily-partition rows for the requested date range across all processing-mode
     * × catalog-item × currency combinations.  Results are ordered by {@code failure_rate DESC}
     * at the DB level so the service need not sort in memory (AC-1).
     *
     * <p>The MV column {@code currency_type} is aliased as {@code currency_id} to match the
     * contract field name ({@code currencyId}) in {@link FailureModeDto}.
     *
     * <p>An optional {@code region} predicate is appended when non-null, constraining results to
     * redemptions by partners in the specified region (AC-3, FR-08.6).
     *
     * @param clientId  current tenant
     * @param dateFrom  inclusive start of the date window
     * @param dateTo    inclusive end of the date window
     * @param region    optional region filter; {@code null} means all regions
     * @return failure mode rows, ordered by failure_rate DESC
     */
    private List<FailureModeDto> queryFailureBreakdown(UUID clientId,
                                                        LocalDate dateFrom,
                                                        LocalDate dateTo,
                                                        String region) {
        StringBuilder sql = new StringBuilder(
                "SELECT processing_mode, catalog_item_id, catalog_item_name, " +
                "       currency_type AS currency_id, " +
                "       SUM(failed_count)    AS failed_count, " +
                "       SUM(cancelled_count) AS cancelled_count, " +
                "       SUM(total_count)     AS total_count, " +
                "       CASE WHEN SUM(total_count) = 0 THEN 0 " +
                "            ELSE ROUND((SUM(failed_count) + SUM(cancelled_count)) * 100.0 " +
                "                       / NULLIF(SUM(total_count), 0), 2) " +
                "       END                  AS failure_rate " +
                "FROM mv_failure_mode_breakdown " +
                "WHERE client_id  = :clientId " +
                "  AND period_date BETWEEN :dateFrom AND :dateTo"
        );

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("clientId", clientId)
                .addValue("dateFrom", dateFrom)
                .addValue("dateTo",   dateTo);

        List<String> regions = parseMultiValue(region);
        if (!regions.isEmpty()) {
            sql.append(" AND region IN (:regions)");
            params.addValue("regions", regions);
        }

        sql.append(" GROUP BY processing_mode, catalog_item_id, catalog_item_name, currency_type" +
                   " ORDER BY failure_rate DESC");

        return namedJdbc.query(sql.toString(), params, (rs, rowNum) ->
                new FailureModeDto(
                        rs.getString("processing_mode"),
                        rs.getString("catalog_item_id"),
                        rs.getString("catalog_item_name"),
                        rs.getString("currency_id"),
                        rs.getLong("failed_count"),
                        rs.getLong("cancelled_count"),
                        rs.getLong("total_count"),
                        rs.getDouble("failure_rate")
                )
        );
    }

    /**
     * Splits a comma-separated multi-value filter (e.g. {@code "APAC,EMEA"}) into a trimmed,
     * de-duplicated list of non-blank values. Returns an empty list when the input is null or
     * blank, in which case the caller omits the predicate entirely (no filter applied).
     *
     * <p>Supports the FE multi-select region/role filters (FR-08.6): the request param arrives
     * as a single comma-separated string and is bound to an {@code IN (:...)} clause.
     */
    private static List<String> parseMultiValue(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
    }

    /**
     * Validates the date range: {@code dateFrom ≤ dateTo} and span ≤ 365 days.
     * Logs a warning with field/value context before throwing per PROJECT-CONTEXT.md convention.
     */
    private void validateDateRange(LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom.isAfter(dateTo)) {
            log.warn("step=advanced_analytics_date_validation_failed field=dateFrom value={} " +
                    "reason=after_dateTo dateTo={}", dateFrom, dateTo);
            throw new BusinessRuleException("dateFrom must not be after dateTo.");
        }
        long spanDays = ChronoUnit.DAYS.between(dateFrom, dateTo);
        if (spanDays > 365) {
            log.warn("step=advanced_analytics_date_validation_failed field=span value={} " +
                    "reason=exceeds_365_days dateFrom={} dateTo={}", spanDays, dateFrom, dateTo);
            throw new BusinessRuleException("Date range must not exceed 365 days.");
        }
    }

    /**
     * Executes the item breakdown native SQL against {@code mv_item_redemption_breakdown}.
     * Appends {@code AND region IN (:regions)} when the {@code region} filter is non-null;
     * the comma-separated request param is split into a value list (FR-08.6 multi-select).
     */
    private List<ItemRedemptionDto> queryItemBreakdown(UUID clientId,
                                                        LocalDate dateFrom,
                                                        LocalDate dateTo,
                                                        String region) {
        // redemption_rate in the MV is stored per row as a percentage (0–100):
        // COMPLETED / COUNT(*) over that row's requests. To produce one rate per
        // (item, currency) we take a count-weighted average of those per-row rates —
        // SUM(count * rate) / SUM(count) — identical to querySegmentBreakdown. The
        // result is always within 0–100.
        //
        // The previous formula derived the rate as
        // (total_redeemed_count - failed_count - cancelled_count) / total_redeemed_count.
        // That only held while total_redeemed_count = COUNT(*) (all statuses). V30
        // redefined total_redeemed_count as COMPLETED-only, so the numerator
        // (completed - failed - cancelled) went negative whenever an item had more
        // failed/cancelled than completed requests — surfacing as negative Rate (%).
        StringBuilder sql = new StringBuilder(
                "SELECT catalog_item_id, catalog_item_name, currency_type AS currency_id, " +
                "       SUM(total_redeemed_count)  AS total_redeemed_count, " +
                "       SUM(total_redeemed_amount) AS total_redeemed_amount, " +
                "       CASE WHEN SUM(total_redeemed_count) = 0 THEN 0 " +
                "            ELSE ROUND(SUM(total_redeemed_count * redemption_rate) " +
                "                       / NULLIF(SUM(total_redeemed_count), 0), 2) " +
                "       END                         AS redemption_rate " +
                "FROM mv_item_redemption_breakdown " +
                "WHERE client_id  = :clientId " +
                "  AND period_date BETWEEN :dateFrom AND :dateTo"
        );

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("clientId", clientId)
                .addValue("dateFrom", dateFrom)
                .addValue("dateTo", dateTo);

        List<String> regions = parseMultiValue(region);
        if (!regions.isEmpty()) {
            sql.append(" AND region IN (:regions)");
            params.addValue("regions", regions);
        }

        sql.append(" GROUP BY catalog_item_id, catalog_item_name, currency_type" +
                   " ORDER BY total_redeemed_count DESC");

        return namedJdbc.query(sql.toString(), params, (rs, rowNum) ->
                new ItemRedemptionDto(
                        rs.getString("catalog_item_id"),
                        rs.getString("catalog_item_name"),
                        rs.getString("currency_id"),
                        rs.getLong("total_redeemed_count"),
                        rs.getBigDecimal("total_redeemed_amount"),
                        rs.getDouble("redemption_rate")
                )
        );
    }

    /**
     * Executes the segment breakdown native SQL against {@code mv_segment_redemption_breakdown}.
     * Appends {@code AND region IN (:regions)} when the {@code region} filter is non-null and
     * {@code AND role IN (:roles)} when the {@code role} filter is non-null. Both request params
     * arrive comma-separated and are split into value lists (FR-08.6 multi-select).
     *
     * <p>The MV stores {@code redemption_rate} as a percentage (0–100 numeric) and the wire
     * format is the same percentage (0–100), matching the contract and the item/trend
     * endpoints — so the row-mapper returns it unscaled.
     */
    private List<SegmentRedemptionDto> querySegmentBreakdown(UUID clientId,
                                                              LocalDate dateFrom,
                                                              LocalDate dateTo,
                                                              String region,
                                                              String role) {
        // redemption_rate in the MV is stored as a percentage (0–100) computed from raw row data.
        // When aggregating across multiple period_date rows, a count-weighted average of the
        // per-day rates is used: SUM(count * rate) / SUM(count). This correctly weights each
        // day by its volume rather than averaging rates equally (which would over-weight low-volume days).
        StringBuilder sql = new StringBuilder(
                "SELECT region, role, currency_type AS currency_id, " +
                "       SUM(total_redeemed_count)  AS redeemed_count, " +
                "       CASE WHEN SUM(total_redeemed_count) = 0 THEN 0 " +
                "            ELSE ROUND(SUM(total_redeemed_count * redemption_rate) " +
                "                       / NULLIF(SUM(total_redeemed_count), 0), 2) " +
                "       END                        AS redemption_rate_pct " +
                "FROM mv_segment_redemption_breakdown " +
                "WHERE client_id   = :clientId " +
                "  AND period_date BETWEEN :dateFrom AND :dateTo"
        );

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("clientId", clientId)
                .addValue("dateFrom", dateFrom)
                .addValue("dateTo", dateTo);

        List<String> regions = parseMultiValue(region);
        if (!regions.isEmpty()) {
            sql.append(" AND region IN (:regions)");
            params.addValue("regions", regions);
        }
        List<String> roles = parseMultiValue(role);
        if (!roles.isEmpty()) {
            sql.append(" AND role IN (:roles)");
            params.addValue("roles", roles);
        }

        sql.append(" GROUP BY region, role, currency_type" +
                   " ORDER BY redeemed_count DESC");

        return namedJdbc.query(sql.toString(), params, (rs, rowNum) -> {
            BigDecimal ratePct = rs.getBigDecimal("redemption_rate_pct");
            BigDecimal rate = ratePct != null ? ratePct : BigDecimal.ZERO;
            return new SegmentRedemptionDto(
                    rs.getString("region"),
                    rs.getString("role"),
                    rs.getString("currency_id"),
                    rs.getLong("redeemed_count"),
                    rate
            );
        });
    }

    /**
     * Queries the minimum {@code last_refreshed_at} from {@code analytics_mv_refresh_log}
     * to surface the MV freshness timestamp per FR-08.8.  Returns {@code null} when the
     * table has no rows (first deploy / no successful refresh yet).
     *
     * <p>Note: this query does NOT gate freshness; it simply reads the current minimum
     * timestamp for inclusion in the response body.  Staleness decisions live in
     * {@link #getRefreshStatus()}.
     *
     * <p>{@code analytics_mv_refresh_log} is a global scheduler log with no {@code client_id}
     * column — the global MIN is returned intentionally (not a tenant-scoping omission).
     */
    @Nullable
    private Instant queryMinLastRefreshedAt() {
        List<Instant> result = namedJdbc.query(
                "SELECT MIN(last_refreshed_at) AS min_ts FROM analytics_mv_refresh_log",
                EmptySqlParameterSource.INSTANCE,
                (rs, rowNum) -> {
                    java.time.OffsetDateTime odt =
                            rs.getObject("min_ts", java.time.OffsetDateTime.class);
                    return odt != null ? odt.toInstant() : null;
                });
        return result.isEmpty() ? null : result.get(0);
    }

    /**
     * Throws {@link AccessDeniedException} when the {@code redemption_analytics_advanced}
     * feature flag is not enabled for {@code clientId}.
     */
    private void requireFeatureEnabled(UUID clientId) {
        if (!featureFlagService.getEnabledFeatures(clientId).contains(FEATURE_FLAG_KEY)) {
            log.warn("step=advanced_analytics_feature_disabled tenantId={} featureFlag={}",
                    clientId, FEATURE_FLAG_KEY);
            throw new AccessDeniedException(
                    "Advanced analytics is not available for your account.");
        }
    }

    /**
     * Queries {@code analytics_mv_refresh_log} and returns a {@link RefreshLogRow} holding
     * both the minimum {@code last_refreshed_at} and the total row count.
     * When the table is empty, {@code mvCount} is 0 and {@code minLastRefreshedAt} is {@code null}.
     */
    private RefreshLogRow queryRefreshLogRow() {
        List<RefreshLogRow> rows = namedJdbc.query(
                REFRESH_STATUS_QUERY,
                EmptySqlParameterSource.INSTANCE,
                (rs, rowNum) -> {
                    java.time.OffsetDateTime odt =
                            rs.getObject("min_last_refreshed_at", java.time.OffsetDateTime.class);
                    Instant minTs = odt != null ? odt.toInstant() : null;
                    int count = rs.getInt("mv_count");
                    return new RefreshLogRow(minTs, count);
                });
        return rows.isEmpty() ? new RefreshLogRow(null, 0) : rows.get(0);
    }

    /**
     * Executes the liability trend native SQL against {@code mv_liability_trend}.
     *
     * <p>Results are ordered by {@code period_date ASC, currency_type ASC} at the DB level,
     * guaranteeing AC-1's "ordered by periodDate ASC" contract without in-memory sorting.
     *
     * <p>The MV column {@code currency_type} is aliased as {@code currency_id} to match the
     * contract field name ({@code currencyId}) in {@link LiabilityDataPointDto}.
     *
     * @param clientId  current tenant
     * @param dateFrom  inclusive start of the date window
     * @param dateTo    inclusive end of the date window
     * @return list of liability data points, ordered period_date ASC, currency_type ASC
     */
    private List<LiabilityDataPointDto> queryLiabilityTrend(UUID clientId,
                                                              LocalDate dateFrom,
                                                              LocalDate dateTo) {
        String sql =
                "SELECT period_date, currency_type AS currency_id, total_unredeemed_balance " +
                "FROM mv_liability_trend " +
                "WHERE client_id  = :clientId " +
                "  AND period_date BETWEEN :dateFrom AND :dateTo " +
                "ORDER BY period_date ASC, currency_type ASC";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("clientId", clientId)
                .addValue("dateFrom", dateFrom)
                .addValue("dateTo",   dateTo);

        return namedJdbc.query(sql, params, (rs, rowNum) -> {
            java.sql.Date sqlDate = rs.getDate("period_date");
            LocalDate periodDate  = sqlDate != null ? sqlDate.toLocalDate() : null;
            return new LiabilityDataPointDto(
                    periodDate,
                    rs.getString("currency_id"),
                    rs.getBigDecimal("total_unredeemed_balance")
            );
        });
    }

    /**
     * Escapes a CSV field value per RFC 4180 and neutralises CSV formula injection
     * (CWE-1236): values whose first character is {@code =}, {@code +}, {@code -},
     * {@code @}, tab, or carriage-return are prefixed with a single-quote so spreadsheet
     * applications do not interpret them as formulas.  Embedded double-quotes are escaped
     * by doubling them.
     *
     * <p>Reused from {@code RedemptionAnalyticsService} per the Security Design spec:
     * "escapeCsv() helper from RedemptionAnalyticsService reused in exportLiabilityTrend()".
     */
    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        // Neutralise formula-injection prefixes before quoting.
        if (!value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0) {
            value = "'" + value;
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /** Holds the aggregate result of a single {@code analytics_mv_refresh_log} query. */
    record RefreshLogRow(Instant minLastRefreshedAt, int mvCount) {}
}
