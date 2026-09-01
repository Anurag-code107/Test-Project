package com.tenxengage.app.service.redemption;

import com.tenxengage.app.dto.response.redemption.CurrencyTypeBalanceDto;
import com.tenxengage.app.dto.response.redemption.CurrencyTypeRateDto;
import com.tenxengage.app.dto.response.redemption.DateWindowDto;
import com.tenxengage.app.dto.response.redemption.RedemptionAnalyticsSummaryResponse;
import com.tenxengage.app.dto.response.redemption.RedemptionCountDto;
import com.tenxengage.app.entity.enums.AuditAction;
import com.tenxengage.app.entity.enums.AuditResourceType;
import com.tenxengage.app.entity.enums.LedgerEntryType;
import com.tenxengage.app.entity.enums.RedemptionStatus;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.repository.LedgerEntryRepository;
import com.tenxengage.app.repository.RedemptionRequestRepository;
import com.tenxengage.app.repository.RewardWalletRepository;
import com.tenxengage.app.repository.projection.BalanceSumProjection;
import com.tenxengage.app.repository.projection.RewardWalletExportProjection;
import com.tenxengage.app.repository.projection.StatusCountProjection;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.AuditLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class RedemptionAnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(RedemptionAnalyticsService.class);

    private static final int MAX_SPAN_DAYS = 730;

    /** Statuses that count as "failed/cancelled" for FR-07.3 */
    private static final List<RedemptionStatus> FAILED_CANCELLED =
            List.of(RedemptionStatus.FAILED, RedemptionStatus.CANCELLED);

    private final LedgerEntryRepository ledgerEntryRepository;
    private final RewardWalletRepository rewardWalletRepository;
    private final RedemptionRequestRepository redemptionRequestRepository;
    private final TenantValidator tenantValidator;
    private final AuditLogService auditLogService;

    public RedemptionAnalyticsService(LedgerEntryRepository ledgerEntryRepository,
                                      RewardWalletRepository rewardWalletRepository,
                                      RedemptionRequestRepository redemptionRequestRepository,
                                      TenantValidator tenantValidator,
                                      AuditLogService auditLogService) {
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.rewardWalletRepository = rewardWalletRepository;
        this.redemptionRequestRepository = redemptionRequestRepository;
        this.tenantValidator = tenantValidator;
        this.auditLogService = auditLogService;
    }

    /**
     * Returns a cache key scoped to the current tenant so that different tenants
     * never share a cached response. Called via SpEL {@code #root.target.buildCacheKey(...)}.
     */
    public String buildCacheKey(LocalDate dateFrom, LocalDate dateTo) {
        UUID clientId = tenantValidator.getCurrentClientId();
        return clientId + ":" + dateFrom + ":" + dateTo;
    }

    /**
     * Returns the redemption analytics summary for the authenticated tenant.
     *
     * <p>Lifetime metrics (redemptionRates, unredeemedBalances) are unaffected by the
     * date window. Windowed metrics (failedCancelledRates, totalRedemptionCount) reflect
     * only requests whose {@code submittedAt} falls within the UTC Instant range derived
     * from the requested {@code dateFrom}/{@code dateTo} window (AC-3).</p>
     *
     * <p>Response is cached in Redis for 60 s, keyed {@code {clientId}:{dateFrom}:{dateTo}}
     * (AC-4).</p>
     *
     * @param dateFrom start of the date window (inclusive); defaults to today−30 if null
     * @param dateTo   end of the date window (inclusive); defaults to today if null
     * @return analytics summary
     * @throws BusinessRuleException if {@code dateFrom} is after {@code dateTo}, or if
     *                               the span exceeds 730 days (422 per AC-5)
     */
    @Cacheable(value = "redemption-analytics", key = "#root.target.buildCacheKey(#dateFrom, #dateTo)")
    public RedemptionAnalyticsSummaryResponse getAnalyticsSummary(LocalDate dateFrom, LocalDate dateTo) {
        long startNs = System.nanoTime();

        UUID clientId = tenantValidator.getCurrentClientId();

        // ── Input validation (AC-5) ───────────────────────────────────────────────
        if (dateFrom.isAfter(dateTo)) {
            log.warn("step=analytics_input_validation_fail reason=dateFrom_after_dateTo clientId={} dateFrom={} dateTo={}",
                     clientId, dateFrom, dateTo);
            throw new BusinessRuleException("dateFrom must not be after dateTo.");
        }
        long spanDays = dateTo.toEpochDay() - dateFrom.toEpochDay();
        if (spanDays > MAX_SPAN_DAYS) {
            log.warn("step=analytics_input_validation_fail reason=span_exceeded clientId={} dateFrom={} dateTo={} spanDays={}",
                     clientId, dateFrom, dateTo, spanDays);
            throw new BusinessRuleException("Date range must not exceed " + MAX_SPAN_DAYS + " days (24 months).");
        }

        // ── Instant range for windowed queries ────────────────────────────────────
        Instant from = dateFrom.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toExclusive = dateTo.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        // ── Active currency IDs (drives all per-currency loops) ───────────────────
        List<String> currencyIds = ledgerEntryRepository.findDistinctCurrencyIdsByClientId(clientId);

        // ── FR-07.1: Redemption rate per currency (lifetime) ─────────────────────
        List<CurrencyTypeRateDto> redemptionRates = new ArrayList<>(currencyIds.size());
        for (String currencyId : currencyIds) {
            BigDecimal earnedBd = ledgerEntryRepository
                    .sumAmountByClientIdAndCurrencyIdAndEntryType(clientId, currencyId, LedgerEntryType.CREDIT);
            BigDecimal redeemedBd = ledgerEntryRepository
                    .sumAmountByClientIdAndCurrencyIdAndEntryType(clientId, currencyId, LedgerEntryType.DEBIT);
            long earned = earnedBd != null ? earnedBd.longValue() : 0L;
            long redeemed = redeemedBd != null ? redeemedBd.longValue() : 0L;
            redemptionRates.add(CurrencyTypeRateDto.of(currencyId, redeemed, earned));
        }

        // ── FR-07.2: Unredeemed balance per currency (snapshot) ──────────────────
        List<CurrencyTypeBalanceDto> unredeemedBalances = new ArrayList<>(currencyIds.size());
        for (String currencyId : currencyIds) {
            BalanceSumProjection sums =
                    rewardWalletRepository.sumBalancesByClientIdAndCurrencyId(clientId, currencyId);
            long available = sums != null && sums.getAvailable() != null
                    ? sums.getAvailable().longValue() : 0L;
            long reserved = sums != null && sums.getReserved() != null
                    ? sums.getReserved().longValue() : 0L;
            unredeemedBalances.add(CurrencyTypeBalanceDto.of(currencyId, available, reserved));
        }

        // ── FR-07.3: Failed/cancelled rate per currency (windowed) ───────────────
        List<CurrencyTypeRateDto> failedCancelledRates = new ArrayList<>(currencyIds.size());
        for (String currencyId : currencyIds) {
            long total = nullToZero(redemptionRequestRepository
                    .countByClientIdAndCurrencyIdAndSubmittedAtBetween(clientId, currencyId, from, toExclusive));
            long failedCancelled = nullToZero(redemptionRequestRepository
                    .countByClientIdAndCurrencyIdAndStatusInAndSubmittedAtBetween(
                            clientId, currencyId, FAILED_CANCELLED, from, toExclusive));
            failedCancelledRates.add(CurrencyTypeRateDto.of(currencyId, failedCancelled, total));
        }

        // ── FR-07.7: Total redemption count with status breakdown (windowed) ──────
        List<StatusCountProjection> rawCounts =
                redemptionRequestRepository.countGroupByStatusByClientIdAndSubmittedAtBetween(
                        clientId, from, toExclusive);

        Map<String, Long> byStatus = buildStatusMap(rawCounts);
        RedemptionCountDto totalRedemptionCount = RedemptionCountDto.of(byStatus);

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("step=analytics_summary_computed clientId={} dateFrom={} dateTo={} durationMs={}",
                clientId, dateFrom, dateTo, durationMs);

        return RedemptionAnalyticsSummaryResponse.of(
                DateWindowDto.of(dateFrom, dateTo),
                redemptionRates,
                unredeemedBalances,
                failedCancelledRates,
                totalRedemptionCount
        );
    }

    /**
     * Exports all unredeemed wallet balances for the authenticated tenant as a UTF-8 CSV
     * byte array. One row per {@code RewardWallet}; individual wallets (null
     * {@code partnerCompanyId}) render {@code companyId=""}, {@code companyName="Individual"}.
     *
     * <p>Not cached — always reflects a live snapshot (AC-1, AC-2).</p>
     *
     * <p>Logs {@code step=analytics_export_downloaded} with {@code tenantId}, {@code userId},
     * and {@code rowCount} <em>after</em> the CSV is built to avoid logging before success
     * (Observability spec).</p>
     *
     * @return UTF-8 encoded CSV bytes, always includes the header row even when 0 wallets exist
     */
    @Transactional(readOnly = false)
    public byte[] exportUnredeemedBalances() {
        UUID clientId = tenantValidator.getCurrentClientId();
        UUID userId   = tenantValidator.getCurrentUserId();

        List<RewardWalletExportProjection> wallets =
                rewardWalletRepository.findAllByClientIdForExport(clientId);

        StringBuilder csv = new StringBuilder();
        csv.append("userId,userName,companyId,companyName,currencyType,availableBalance,reservedBalance\n");

        for (RewardWalletExportProjection wallet : wallets) {
            String companyId   = wallet.getCompanyId() != null ? wallet.getCompanyId().toString() : "";
            String companyName = wallet.getCompanyName() != null ? wallet.getCompanyName() : "Individual";
            String currencyType = wallet.getCurrencyType() != null ? wallet.getCurrencyType() : "";
            long available = wallet.getAvailableBalance() != null
                    ? wallet.getAvailableBalance().longValue() : 0L;
            long reserved = wallet.getReservedBalance() != null
                    ? wallet.getReservedBalance().longValue() : 0L;

            csv.append(wallet.getUserId()).append(',')
               .append(escapeCsv(wallet.getUserName())).append(',')
               .append(companyId).append(',')
               .append(escapeCsv(companyName)).append(',')
               .append(escapeCsv(currencyType)).append(',')
               .append(available).append(',')
               .append(reserved).append('\n');
        }

        byte[] result = csv.toString().getBytes(StandardCharsets.UTF_8);

        // Log the business event after the CSV is built (not before — avoid logging before success)
        log.info("step=analytics_export_downloaded tenantId={} userId={} rowCount={}",
                 clientId, userId, wallets.size());

        // Emit audit record: DATA_EXPORTED / REDEMPTION_ANALYTICS_EXPORT (AC-3, Audit Trail spec).
        // Called here in the service (after success) so that 403 (permission-rejected before service call)
        // and 429 (rate-limited before service call) never trigger an audit row.
        auditLogService.logAsync(
                AuditAction.DATA_EXPORTED,
                AuditResourceType.REDEMPTION_ANALYTICS_EXPORT,
                null,
                null,
                "Analytics unredeemed balance export downloaded",
                Map.of("rowCount", wallets.size(), "tenantId", clientId.toString(), "userId", userId.toString())
        );

        return result;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────

    /**
     * Builds the aggregated status map expected by FR-07.7.
     * PENDING_APPROVAL and RESERVED are merged into the single "PENDING" display key.
     */
    private Map<String, Long> buildStatusMap(List<StatusCountProjection> projections) {
        // Use a mutable map for accumulation; callers of RedemptionCountDto.of() get an unmodifiable copy.
        Map<String, Long> map = new LinkedHashMap<>();
        map.put("PENDING", 0L);
        map.put("PROCESSING", 0L);
        map.put("COMPLETED", 0L);
        map.put("FAILED", 0L);
        map.put("CANCELLED", 0L);

        for (StatusCountProjection p : projections) {
            RedemptionStatus status = p.getStatus();
            long count = p.getCount() != null ? p.getCount() : 0L;
            switch (status) {
                case PENDING_APPROVAL, RESERVED -> map.merge("PENDING", count, Long::sum);
                case PROCESSING -> map.merge("PROCESSING", count, Long::sum);
                case COMPLETED -> map.merge("COMPLETED", count, Long::sum);
                case FAILED -> map.merge("FAILED", count, Long::sum);
                case CANCELLED -> map.merge("CANCELLED", count, Long::sum);
            }
        }
        return map;
    }

    private long nullToZero(Long value) {
        return value != null ? value : 0L;
    }

    /**
     * Escapes a CSV field value per RFC 4180 and neutralises CSV formula injection
     * (CWE-1236): values whose first character is {@code =}, {@code +}, {@code -}, or
     * {@code @} are prefixed with a single-quote so spreadsheet applications do not
     * interpret them as formulas. Embedded double-quotes are escaped by doubling them.
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
}
