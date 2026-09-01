package com.tenxengage.app.service.redemption;

import com.tenxengage.app.dto.response.BalanceBreakageReportResponse;
import com.tenxengage.app.dto.response.BreakageRowDto;
import com.tenxengage.app.entity.enums.AuditAction;
import com.tenxengage.app.entity.enums.AuditResourceType;
import com.tenxengage.app.entity.enums.Granularity;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.repository.LedgerEntryRepository;
import com.tenxengage.app.repository.projection.BreakageRowProjection;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.AuditLogService;
import com.tenxengage.app.service.FeatureFlagService;
import com.tenxengage.app.util.CsvUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for the balance expiration breakage report (FR-09.6).
 *
 * <p>Aggregates {@code LedgerEntry} rows where {@code entry_type = EXPIRY} using the native
 * {@code aggregateExpiryBreakage} query, bucketed by the requested {@link Granularity}
 * (MONTH or QUARTER) and filtered by date range and optional currency type.
 *
 * <p>Tenant isolation: {@code clientId} is resolved from the JWT via
 * {@link TenantValidator#getCurrentClientId()} — never from a header.</p>
 *
 * <p>CSV export uses {@link CsvUtil#escapeCsv} on all string cells for formula-injection
 * safety (CWE-1236).</p>
 */
@Service
@Transactional(readOnly = true)
public class BalanceBreakageReportService {

    private static final Logger log = LoggerFactory.getLogger(BalanceBreakageReportService.class);

    private static final String FEATURE_KEY = "reward_balance_expiration";
    private static final long MAX_RANGE_MONTHS = 24L;

    private static final Map<String, String> CURRENCY_DISPLAY_NAMES = Map.of(
            "cash", "Cash",
            "points", "Points",
            "credits", "Credits",
            "tickets", "Tickets"
    );

    private static final String CSV_HEADER =
            "period_start,period_end,currency_id,expired_count,total_expired_amount\n";

    private final LedgerEntryRepository ledgerEntryRepository;
    private final TenantValidator tenantValidator;
    private final FeatureFlagService featureFlagService;
    private final AuditLogService auditLogService;

    public BalanceBreakageReportService(LedgerEntryRepository ledgerEntryRepository,
                                         TenantValidator tenantValidator,
                                         FeatureFlagService featureFlagService,
                                         AuditLogService auditLogService) {
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.tenantValidator = tenantValidator;
        this.featureFlagService = featureFlagService;
        this.auditLogService = auditLogService;
    }

    /**
     * Returns the breakage report for the authenticated tenant, aggregated by
     * currency type and period bucket.
     *
     * @param from        inclusive report start date (required)
     * @param to          inclusive report end date (required); must not be before {@code from}
     * @param currencyId  optional currency filter; null means all currencies
     * @param granularity period bucketing — MONTH or QUARTER; defaults to MONTH when null
     * @return breakage report; {@code rows} is empty when no EXPIRY entries exist in range
     * @throws BusinessRuleException {@code ERR_INVALID_DATE_RANGE} when {@code to < from}
     *                               or range exceeds 24 months
     */
    public BalanceBreakageReportResponse getBreakage(LocalDate from,
                                                      LocalDate to,
                                                      String currencyId,
                                                      Granularity granularity) {
        UUID clientId = tenantValidator.getCurrentClientId();
        checkFeatureEnabled(clientId);

        Granularity effectiveGranularity = granularity != null ? granularity : Granularity.MONTH;
        validateDateRange(from, to, clientId);

        Instant fromInstant = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toExclusive = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<BreakageRowProjection> projections = ledgerEntryRepository.aggregateExpiryBreakage(
                clientId, fromInstant, toExclusive, currencyId, effectiveGranularity.toDateTruncBucket());

        List<BreakageRowDto> rows = projections.stream()
                .map(p -> toBreakageRowDto(p, effectiveGranularity))
                .toList();

        log.info("step=breakage_report_generated clientId={} from={} to={} granularity={} rowCount={}",
                clientId, from, to, effectiveGranularity, rows.size());

        return BalanceBreakageReportResponse.from(from, to, effectiveGranularity, rows);
    }

    /**
     * Exports the breakage report as a UTF-8 CSV string for the authenticated tenant.
     *
     * <p>All string cells are escaped via {@link CsvUtil#escapeCsv} (CWE-1236). The returned
     * string includes the header row even when there are no data rows.</p>
     *
     * <p>Rate-limiting is enforced at the controller layer before this method is invoked.
     * The audit record ({@code DATA_EXPORTED / BALANCE_EXPIRY_BREAKAGE_EXPORT}) is emitted
     * HERE, after the CSV is built, so that 403 (permission-rejected before service call)
     * and 429 (rate-limited before service call, early-return before reaching this method)
     * paths never produce an audit row. {@code @Audited} on the controller method cannot
     * provide this guarantee because the aspect fires on any normal return — including 429
     * {@code ResponseEntity} values — not only on successful 200 returns.</p>
     *
     * @param from        inclusive report start date
     * @param to          inclusive report end date
     * @param currencyId  optional currency filter
     * @param granularity period bucketing
     * @return UTF-8 CSV string with header row
     */
    public String exportBreakageCsv(LocalDate from,
                                     LocalDate to,
                                     String currencyId,
                                     Granularity granularity) {
        BalanceBreakageReportResponse report = getBreakage(from, to, currencyId, granularity);

        StringBuilder csv = new StringBuilder(CSV_HEADER);
        for (BreakageRowDto row : report.rows()) {
            csv.append(CsvUtil.escapeCsv(row.periodStart() != null ? row.periodStart().toString() : "")).append(',')
               .append(CsvUtil.escapeCsv(row.periodEnd() != null ? row.periodEnd().toString() : "")).append(',')
               .append(CsvUtil.escapeCsv(row.currencyId())).append(',')
               .append(row.expiredCount()).append(',')
               .append(CsvUtil.escapeCsv(row.totalExpiredAmount() != null ? row.totalExpiredAmount().toPlainString() : "0"))
               .append('\n');
        }

        String csvResult = csv.toString();

        UUID clientId = tenantValidator.getCurrentClientId();
        log.info("step=breakage_csv_exported clientId={} from={} to={} granularity={} rowCount={}",
                clientId, from, to, granularity, report.rows().size());

        // Emit audit record AFTER the CSV is built and before returning (not via @Audited on the
        // controller method) — ensures 403 and 429 paths never produce an audit row (AC-2, spec
        // § Audit Trail: "403 and 429 never produce an audit row").
        auditLogService.logAsync(
                AuditAction.DATA_EXPORTED,
                AuditResourceType.BALANCE_EXPIRY_BREAKAGE_EXPORT,
                null,
                null,
                "Exported balance expiration breakage report",
                Map.of("from", from.toString(), "to", to.toString(),
                       "granularity", report.granularity().name(),
                       "rowCount", report.rows().size(),
                       "clientId", clientId.toString())
        );

        return csvResult;
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private void checkFeatureEnabled(UUID clientId) {
        List<String> enabled = featureFlagService.getEnabledFeatures(clientId);
        if (!enabled.contains(FEATURE_KEY)) {
            throw new BusinessRuleException("FEATURE_DISABLED",
                    "The reward_balance_expiration feature is not enabled for this tenant");
        }
    }

    /**
     * Validates that {@code to ≥ from} and that the range does not exceed 24 months.
     * Throws {@link BusinessRuleException} with {@code ERR_INVALID_DATE_RANGE} on failure.
     */
    private void validateDateRange(LocalDate from, LocalDate to, UUID clientId) {
        if (to.isBefore(from)) {
            log.warn("step=breakage_range_invalid reason=to_before_from clientId={} from={} to={}",
                    clientId, from, to);
            throw new BusinessRuleException("ERR_INVALID_DATE_RANGE",
                    "End date must be on or after start date");
        }
        // Check months: toEpochDay difference used for day granularity, but spec caps at 24 calendar months.
        // Compare by calendar months: to must not be more than 24 months after from.
        LocalDate maxTo = from.plusMonths(MAX_RANGE_MONTHS);
        if (to.isAfter(maxTo)) {
            log.warn("step=breakage_range_invalid reason=range_exceeds_24_months clientId={} from={} to={}",
                    clientId, from, to);
            throw new BusinessRuleException("ERR_INVALID_DATE_RANGE",
                    "Range cannot exceed 24 months");
        }
    }

    /**
     * Maps a {@link BreakageRowProjection} from the native query to a {@link BreakageRowDto},
     * computing {@code periodEnd} and resolving {@code currencyDisplayName}.
     *
     * <p>The native query returns {@code period_start} via {@code date_trunc(:bucket, ...)}
     * which is always the first day of the month or quarter. {@code period_end} is derived
     * by advancing to the last day of the corresponding period bucket.</p>
     */
    private BreakageRowDto toBreakageRowDto(BreakageRowProjection projection, Granularity granularity) {
        LocalDate periodStart = projection.getPeriodStart();
        LocalDate periodEnd = computePeriodEnd(periodStart, granularity);

        String currencyId = projection.getCurrencyId();
        String displayName = CURRENCY_DISPLAY_NAMES.getOrDefault(
                currencyId != null ? currencyId.toLowerCase() : "",
                currencyId
        );

        BigDecimal totalExpiredAmount = projection.getTotalExpiredAmount();
        if (totalExpiredAmount == null) {
            totalExpiredAmount = BigDecimal.ZERO;
        }

        return new BreakageRowDto(
                periodStart,
                periodEnd,
                currencyId,
                displayName,
                projection.getExpiredCount(),
                totalExpiredAmount
        );
    }

    /**
     * Computes the last day of the period bucket that starts on {@code periodStart}.
     *
     * <ul>
     *   <li>MONTH: last day of the same calendar month</li>
     *   <li>QUARTER: last day of the calendar quarter that contains {@code periodStart}</li>
     * </ul>
     */
    private static LocalDate computePeriodEnd(LocalDate periodStart, Granularity granularity) {
        if (periodStart == null) {
            return null;
        }
        return switch (granularity) {
            case MONTH -> periodStart.with(TemporalAdjusters.lastDayOfMonth());
            case QUARTER -> {
                // Quarter ends: Q1→Mar31, Q2→Jun30, Q3→Sep30, Q4→Dec31
                int month = periodStart.getMonthValue();
                int endMonth = ((month - 1) / 3 + 1) * 3; // 3, 6, 9, or 12
                yield LocalDate.of(periodStart.getYear(), endMonth, 1)
                        .with(TemporalAdjusters.lastDayOfMonth());
            }
        };
    }
}
