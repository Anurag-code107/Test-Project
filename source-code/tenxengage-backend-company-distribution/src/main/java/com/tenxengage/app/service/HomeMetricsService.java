package com.tenxengage.app.service;

import com.tenxengage.app.dto.response.HomeRewardBreakdownData;
import com.tenxengage.app.dto.response.IncentivePerformanceResponse;
import com.tenxengage.app.dto.response.MetricResponse;
import com.tenxengage.app.dto.response.ParticipationMetricsResponse;
import com.tenxengage.app.dto.response.ProgramPerformanceResponse;
import com.tenxengage.app.dto.response.TrendDataPoint;
import com.tenxengage.app.entity.enums.CurrencyType;
import com.tenxengage.app.entity.enums.HomeDateFilter;
import com.tenxengage.app.entity.enums.HomeIncentiveTypeFilter;
import com.tenxengage.app.entity.enums.IncentiveType;
import com.tenxengage.app.entity.Currency;
import com.tenxengage.app.entity.FiscalYearConfig;
import com.tenxengage.app.repository.CurrencyRepository;
import com.tenxengage.app.repository.FiscalYearConfigRepository;
import com.tenxengage.app.security.TenantValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class HomeMetricsService {

    private static final Logger log = LoggerFactory.getLogger(HomeMetricsService.class);

    private final JdbcTemplate jdbcTemplate;
    private final TenantValidator tenantValidator;
    private final FiscalYearConfigRepository fiscalYearConfigRepository;
    private final CurrencyRepository currencyRepository;

    public HomeMetricsService(JdbcTemplate jdbcTemplate,
                              TenantValidator tenantValidator,
                              FiscalYearConfigRepository fiscalYearConfigRepository,
                              CurrencyRepository currencyRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.tenantValidator = tenantValidator;
        this.fiscalYearConfigRepository = fiscalYearConfigRepository;
        this.currencyRepository = currencyRepository;
    }

    // ── Date range resolution ───────────────────────────────────────────────────

    public record DateRange(LocalDate start, LocalDate end) {}

    public DateRange resolveDateRange(HomeDateFilter filter, LocalDate startDate, LocalDate endDate) {
        LocalDate now = LocalDate.now();
        return switch (filter) {
            case LAST_30_DAYS -> new DateRange(now.minusDays(30), now);
            case THIS_QUARTER -> {
                // Try fiscal-aware quarter lookup first
                LocalDate qStart = resolveFiscalQuarterStart(now);
                yield new DateRange(qStart, now);
            }
            case THIS_YEAR -> new DateRange(LocalDate.of(now.getYear(), 1, 1), now);
            case CUSTOM -> new DateRange(
                startDate != null ? startDate : now.minusDays(30),
                endDate != null ? endDate : now
            );
        };
    }

    /**
     * Resolve the start date of the fiscal quarter containing the given date.
     * Falls back to calendar quarter math if no fiscal config exists.
     */
    private LocalDate resolveFiscalQuarterStart(LocalDate date) {
        try {
            UUID clientId = tenantValidator.getCurrentClientId();
            List<FiscalYearConfig> configs = fiscalYearConfigRepository
                .findByClientIdOrderByStartDateAsc(clientId);
            for (FiscalYearConfig c : configs) {
                if (!date.isBefore(c.getStartDate()) && !date.isAfter(c.getEndDate())) {
                    if (!date.isBefore(c.getQ4StartDate())) return c.getQ4StartDate();
                    if (!date.isBefore(c.getQ3StartDate())) return c.getQ3StartDate();
                    if (!date.isBefore(c.getQ2StartDate())) return c.getQ2StartDate();
                    return c.getQ1StartDate();
                }
            }
        } catch (Exception e) {
            log.debug("Could not resolve fiscal quarter, falling back to calendar: {}", e.getMessage());
        }
        // Calendar fallback
        int quarter = (date.getMonthValue() - 1) / 3;
        return LocalDate.of(date.getYear(), quarter * 3 + 1, 1);
    }

    public DateRange previousPeriod(DateRange current) {
        long days = ChronoUnit.DAYS.between(current.start(), current.end());
        return new DateRange(current.start().minusDays(days), current.start().minusDays(1));
    }

    // ── Trend data point generation ─────────────────────────────────────────────

    private String trendInterval(DateRange range) {
        long days = ChronoUnit.DAYS.between(range.start(), range.end());
        if (days <= 14) return "day";
        if (days <= 90) return "week";
        return "month";
    }

    private String formatLabel(String interval, LocalDate date) {
        return switch (interval) {
            case "day" -> date.format(DateTimeFormatter.ofPattern("MMM d"));
            case "week" -> "Wk " + date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            case "month" -> date.format(DateTimeFormatter.ofPattern("MMM"));
            default -> date.toString();
        };
    }

    // ── Participation metrics ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ParticipationMetricsResponse getParticipationMetrics(
            HomeDateFilter dateFilter, LocalDate startDate, LocalDate endDate,
            String region, UUID partnerCompanyId) {

        UUID clientId = tenantValidator.getCurrentClientId();
        DateRange current = resolveDateRange(dateFilter, startDate, endDate);
        DateRange previous = previousPeriod(current);
        boolean isGlobal = region == null || "GLOBAL".equalsIgnoreCase(region);

        if (partnerCompanyId != null) {
            return buildPartnerParticipation(clientId, partnerCompanyId, current, previous, isGlobal ? null : region);
        }
        return buildGlobalParticipation(clientId, current, previous, isGlobal ? null : region);
    }

    private ParticipationMetricsResponse buildGlobalParticipation(
            UUID clientId, DateRange current, DateRange previous, String region) {

        // 1. Partner Companies Enrolled
        long companiesCurrent = countPartnerCompanies(clientId, region);
        long companiesPrev = countPartnerCompaniesAsOf(clientId, region, previous.end());
        List<TrendDataPoint> companiesTrend = buildEnrollmentTrend(
            clientId, region, current, "partner_companies", null);
        MetricResponse companiesMetric = new MetricResponse(
            BigDecimal.valueOf(companiesCurrent), null,
            trendPercent(companiesCurrent, companiesPrev), companiesTrend);

        // 2. Partner Users Enrolled
        long usersCurrent = countPartnerUsers(clientId, region, null);
        long usersPrev = countPartnerUsersAsOf(clientId, region, null, previous.end());
        List<TrendDataPoint> usersTrend = buildEnrollmentTrend(
            clientId, region, current, "users", null);
        MetricResponse usersMetric = new MetricResponse(
            BigDecimal.valueOf(usersCurrent), null,
            trendPercent(usersCurrent, usersPrev), usersTrend);

        // 3. Companies Earning Rewards
        long companiesEarning = countCompaniesEarning(clientId, region, current);
        long companiesEarningPrev = countCompaniesEarning(clientId, region, previous);
        BigDecimal earningPercent = companiesCurrent > 0
            ? BigDecimal.valueOf(companiesEarning * 100).divide(BigDecimal.valueOf(companiesCurrent), 0, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;
        List<TrendDataPoint> earningTrend = buildCompaniesEarningTrend(clientId, region, current);
        MetricResponse earningMetric = new MetricResponse(
            earningPercent,
            "(" + companiesEarning + " of " + companiesCurrent + " companies)",
            trendPercent(companiesEarning, companiesEarningPrev), earningTrend);

        return ParticipationMetricsResponse.global(companiesMetric, usersMetric, earningMetric);
    }

    private ParticipationMetricsResponse buildPartnerParticipation(
            UUID clientId, UUID partnerCompanyId, DateRange current, DateRange previous, String region) {

        // 1. Partner Enrolled Users
        long usersCurrent = countPartnerUsers(clientId, region, partnerCompanyId);
        long usersPrev = countPartnerUsersAsOf(clientId, region, partnerCompanyId, previous.end());
        List<TrendDataPoint> usersTrend = buildEnrollmentTrend(
            clientId, region, current, "users", partnerCompanyId);
        MetricResponse usersMetric = new MetricResponse(
            BigDecimal.valueOf(usersCurrent), null,
            trendPercent(usersCurrent, usersPrev), usersTrend);

        // 2. Users Earning Rewards
        long usersEarning = countUsersEarningForPartner(clientId, partnerCompanyId, current);
        long usersEarningPrev = countUsersEarningForPartner(clientId, partnerCompanyId, previous);
        BigDecimal earningPercent = usersCurrent > 0
            ? BigDecimal.valueOf(usersEarning * 100).divide(BigDecimal.valueOf(usersCurrent), 0, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;
        List<TrendDataPoint> earningTrend = buildUsersEarningTrend(clientId, partnerCompanyId, current);
        MetricResponse earningMetric = new MetricResponse(
            earningPercent,
            "(" + usersEarning + " of " + usersCurrent + " users)",
            trendPercent(usersEarning, usersEarningPrev), earningTrend);

        // 3. User Claims Made
        long claimsCurrent = countClaimsForPartner(clientId, partnerCompanyId, current);
        long claimsPrev = countClaimsForPartner(clientId, partnerCompanyId, previous);
        List<TrendDataPoint> claimsTrend = buildClaimsTrend(clientId, partnerCompanyId, current);
        MetricResponse claimsMetric = new MetricResponse(
            BigDecimal.valueOf(claimsCurrent), null,
            trendPercent(claimsCurrent, claimsPrev), claimsTrend);

        return ParticipationMetricsResponse.forPartner(usersMetric, earningMetric, claimsMetric);
    }

    // ── Incentive performance metrics ───────────────────────────────────────────

    @Transactional(readOnly = true)
    public IncentivePerformanceResponse getIncentivePerformance(
            HomeDateFilter dateFilter, LocalDate startDate, LocalDate endDate,
            String region, UUID partnerCompanyId, HomeIncentiveTypeFilter incentiveType) {

        UUID clientId = tenantValidator.getCurrentClientId();
        Set<String> monetaryCodes = currencyRepository.findByClientIdAndType(clientId, CurrencyType.MONETARY)
            .stream().map(Currency::getCode).collect(Collectors.toSet());
        DateRange current = resolveDateRange(dateFilter, startDate, endDate);
        DateRange previous = previousPeriod(current);
        boolean isGlobal = region == null || "GLOBAL".equalsIgnoreCase(region);
        String regionParam = isGlobal ? null : region;
        List<IncentiveType> types = incentiveType.toIncentiveTypes();

        // 1. Total Rewards Earned (monetary sum) + breakdown
        Map<String, CurrencyAggregates> currencyTotals = getRewardsByCurrency(
            clientId, regionParam, partnerCompanyId, types, current);
        BigDecimal totalMonetary = currencyTotals.entrySet().stream()
            .filter(e -> monetaryCodes.contains(e.getKey()))
            .map(e -> e.getValue().sum())
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, CurrencyAggregates> prevCurrencyTotals = getRewardsByCurrency(
            clientId, regionParam, partnerCompanyId, types, previous);
        BigDecimal prevTotalMonetary = prevCurrencyTotals.entrySet().stream()
            .filter(e -> monetaryCodes.contains(e.getKey()))
            .map(e -> e.getValue().sum())
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<TrendDataPoint> rewardsTrend = buildRewardsTrend(
            clientId, regionParam, partnerCompanyId, types, current);
        MetricResponse rewardsMetric = new MetricResponse(
            totalMonetary, null,
            trendPercent(totalMonetary, prevTotalMonetary), rewardsTrend);

        HomeRewardBreakdownData breakdown = buildRewardBreakdown(clientId, currencyTotals, totalMonetary);

        // 2. Budget Utilized
        BigDecimal totalBudget = getTotalBudget(clientId, types, regionParam);
        BigDecimal totalUtilized = getTotalUtilized(clientId, types, regionParam);
        BigDecimal budgetPercent = totalBudget.compareTo(BigDecimal.ZERO) > 0
            ? totalUtilized.multiply(BigDecimal.valueOf(100)).divide(totalBudget, 0, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        BigDecimal prevUtilized = getRewardsTotal(clientId, regionParam, partnerCompanyId, types, previous);
        BigDecimal prevBudgetPercent = totalBudget.compareTo(BigDecimal.ZERO) > 0
            ? (totalUtilized.subtract(getRewardsTotal(clientId, regionParam, partnerCompanyId, types, current))
                .add(prevUtilized))
                .multiply(BigDecimal.valueOf(100)).divide(totalBudget, 0, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        List<TrendDataPoint> budgetTrend = buildBudgetTrend(clientId, regionParam, partnerCompanyId, types, current, totalBudget);
        MetricResponse budgetMetric = new MetricResponse(
            budgetPercent,
            "of $" + totalBudget.setScale(0, RoundingMode.HALF_UP).toPlainString(),
            trendPercent(budgetPercent, prevBudgetPercent), budgetTrend);

        // 3. Users Participating
        long totalUsers = countPartnerUsers(clientId, regionParam, partnerCompanyId);
        long participatingCurrent = countUsersParticipating(clientId, regionParam, partnerCompanyId, types, current);
        long participatingPrev = countUsersParticipating(clientId, regionParam, partnerCompanyId, types, previous);
        BigDecimal participatingPercent = totalUsers > 0
            ? BigDecimal.valueOf(participatingCurrent * 100).divide(BigDecimal.valueOf(totalUsers), 0, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;
        List<TrendDataPoint> participatingTrend = buildUsersParticipatingTrend(
            clientId, regionParam, partnerCompanyId, types, current, totalUsers);
        MetricResponse participatingMetric = new MetricResponse(
            participatingPercent, null,
            trendPercent(participatingCurrent, participatingPrev), participatingTrend);

        return new IncentivePerformanceResponse(
            rewardsMetric, budgetMetric, participatingMetric, breakdown, totalBudget);
    }

    // ── SQL query helpers ───────────────────────────────────────────────────────

    private long countPartnerCompanies(UUID clientId, String region) {
        String sql = "SELECT COUNT(*) FROM partner_companies WHERE client_id = ? AND status = 'ACTIVE'"
            + (region != null ? " AND region = ?" : "");
        return region != null
            ? jdbcTemplate.queryForObject(sql, Long.class, clientId, region)
            : jdbcTemplate.queryForObject(sql, Long.class, clientId);
    }

    private long countPartnerCompaniesAsOf(UUID clientId, String region, LocalDate asOf) {
        String sql = "SELECT COUNT(*) FROM partner_companies WHERE client_id = ? AND status = 'ACTIVE' AND created_at <= ?"
            + (region != null ? " AND region = ?" : "");
        return region != null
            ? jdbcTemplate.queryForObject(sql, Long.class, clientId, asOf.plusDays(1).atStartOfDay(), region)
            : jdbcTemplate.queryForObject(sql, Long.class, clientId, asOf.plusDays(1).atStartOfDay());
    }

    private long countPartnerUsers(UUID clientId, String region, UUID partnerCompanyId) {
        StringBuilder sql = new StringBuilder(
            "SELECT COUNT(*) FROM users u WHERE u.client_id = ? AND u.status = 'ACTIVE'");
        List<Object> params = new ArrayList<>();
        params.add(clientId);

        if (partnerCompanyId != null) {
            sql.append(" AND u.partner_company_id = ?");
            params.add(partnerCompanyId);
        } else {
            sql.append(" AND u.partner_company_id IN (SELECT id FROM partner_companies WHERE client_id = ? AND status = 'ACTIVE'");
            params.add(clientId);
            if (region != null) {
                sql.append(" AND region = ?");
                params.add(region);
            }
            sql.append(")");
        }
        return jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
    }

    private long countPartnerUsersAsOf(UUID clientId, String region, UUID partnerCompanyId, LocalDate asOf) {
        StringBuilder sql = new StringBuilder(
            "SELECT COUNT(*) FROM users u WHERE u.client_id = ? AND u.status = 'ACTIVE' AND u.created_at <= ?");
        List<Object> params = new ArrayList<>();
        params.add(clientId);
        params.add(asOf.plusDays(1).atStartOfDay());

        if (partnerCompanyId != null) {
            sql.append(" AND u.partner_company_id = ?");
            params.add(partnerCompanyId);
        } else {
            sql.append(" AND u.partner_company_id IN (SELECT id FROM partner_companies WHERE client_id = ? AND status = 'ACTIVE'");
            params.add(clientId);
            if (region != null) {
                sql.append(" AND region = ?");
                params.add(region);
            }
            sql.append(")");
        }
        return jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
    }

    private long countCompaniesEarning(UUID clientId, String locationValueId, DateRange range) {
        String sql = """
            SELECT COUNT(DISTINCT pc.id)
            FROM partner_companies pc
            JOIN users u ON u.partner_company_id = pc.id AND u.client_id = pc.client_id
            JOIN reward_transactions rt ON rt.user_id = u.id AND rt.client_id = u.client_id
            WHERE pc.client_id = ? AND pc.status = 'ACTIVE'
            AND u.status = 'ACTIVE'
            AND rt.created_at >= ? AND rt.created_at < ?
            """ + (locationValueId != null ? " AND pc.id IN (SELECT partner_company_id FROM partner_company_locations WHERE location_value_id = ?::uuid)" : "");
        return locationValueId != null
            ? jdbcTemplate.queryForObject(sql, Long.class, clientId,
                range.start().atStartOfDay(), range.end().plusDays(1).atStartOfDay(), locationValueId)
            : jdbcTemplate.queryForObject(sql, Long.class, clientId,
                range.start().atStartOfDay(), range.end().plusDays(1).atStartOfDay());
    }

    private long countUsersEarningForPartner(UUID clientId, UUID partnerCompanyId, DateRange range) {
        String sql = """
            SELECT COUNT(DISTINCT rt.user_id)
            FROM reward_transactions rt
            JOIN users u ON u.id = rt.user_id AND u.client_id = rt.client_id
            WHERE rt.client_id = ? AND u.partner_company_id = ?
            AND u.status = 'ACTIVE'
            AND rt.created_at >= ? AND rt.created_at < ?
            """;
        return jdbcTemplate.queryForObject(sql, Long.class, clientId, partnerCompanyId,
            range.start().atStartOfDay(), range.end().plusDays(1).atStartOfDay());
    }

    private long countClaimsForPartner(UUID clientId, UUID partnerCompanyId, DateRange range) {
        String sql = """
            SELECT COUNT(*)
            FROM claim_actions ca
            JOIN purchase_orders po ON po.id = ca.purchase_order_id
            WHERE ca.client_id = ? AND po.partner_company_id = ?
            AND ca.claimed_at >= ? AND ca.claimed_at < ?
            """;
        return jdbcTemplate.queryForObject(sql, Long.class, clientId, partnerCompanyId,
            range.start().atStartOfDay(), range.end().plusDays(1).atStartOfDay());
    }

    private long countUsersParticipating(UUID clientId, String region, UUID partnerCompanyId,
                                          List<IncentiveType> types, DateRange range) {
        StringBuilder sql = new StringBuilder("""
            SELECT COUNT(DISTINCT rt.user_id)
            FROM reward_transactions rt
            JOIN incentives i ON i.id = rt.incentive_id
            JOIN users u ON u.id = rt.user_id AND u.client_id = rt.client_id
            WHERE rt.client_id = ? AND rt.created_at >= ? AND rt.created_at < ?
            AND i.deleted = false
            """);
        List<Object> params = new ArrayList<>();
        params.add(clientId);
        params.add(range.start().atStartOfDay());
        params.add(range.end().plusDays(1).atStartOfDay());

        appendIncentiveTypeFilter(sql, params, types);
        appendRegionFilter(sql, params, region, clientId);
        appendPartnerFilter(sql, params, partnerCompanyId);

        return jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
    }

    private record CurrencyAggregates(BigDecimal sum, long count) {}

    private Map<String, CurrencyAggregates> getRewardsByCurrency(UUID clientId, String region, UUID partnerCompanyId,
                                                          List<IncentiveType> types, DateRange range) {
        StringBuilder sql = new StringBuilder("""
            SELECT rt.currency_id, COALESCE(SUM(rt.amount_awarded), 0), COUNT(*)
            FROM reward_transactions rt
            JOIN incentives i ON i.id = rt.incentive_id
            JOIN users u ON u.id = rt.user_id AND u.client_id = rt.client_id
            WHERE rt.client_id = ? AND rt.created_at >= ? AND rt.created_at < ?
            AND i.deleted = false
            """);
        List<Object> params = new ArrayList<>();
        params.add(clientId);
        params.add(range.start().atStartOfDay());
        params.add(range.end().plusDays(1).atStartOfDay());

        appendIncentiveTypeFilter(sql, params, types);
        appendRegionFilter(sql, params, region, clientId);
        appendPartnerFilter(sql, params, partnerCompanyId);

        sql.append(" GROUP BY rt.currency_id");

        Map<String, CurrencyAggregates> result = new LinkedHashMap<>();
        jdbcTemplate.query(sql.toString(), params.toArray(), rs -> {
            result.put(rs.getString(1), new CurrencyAggregates(rs.getBigDecimal(2), rs.getLong(3)));
        });
        return result;
    }

    private BigDecimal getRewardsTotal(UUID clientId, String region, UUID partnerCompanyId,
                                        List<IncentiveType> types, DateRange range) {
        StringBuilder sql = new StringBuilder("""
            SELECT COALESCE(SUM(rt.amount_awarded), 0)
            FROM reward_transactions rt
            JOIN incentives i ON i.id = rt.incentive_id
            JOIN users u ON u.id = rt.user_id AND u.client_id = rt.client_id
            WHERE rt.client_id = ? AND rt.created_at >= ? AND rt.created_at < ?
            AND i.deleted = false
            """);
        List<Object> params = new ArrayList<>();
        params.add(clientId);
        params.add(range.start().atStartOfDay());
        params.add(range.end().plusDays(1).atStartOfDay());

        appendIncentiveTypeFilter(sql, params, types);
        appendRegionFilter(sql, params, region, clientId);
        appendPartnerFilter(sql, params, partnerCompanyId);

        return jdbcTemplate.queryForObject(sql.toString(), BigDecimal.class, params.toArray());
    }

    private BigDecimal getTotalBudget(UUID clientId, List<IncentiveType> types, String region) {
        StringBuilder sql = new StringBuilder("""
            SELECT COALESCE(SUM(ib.total_budget), 0)
            FROM incentive_budgets ib
            JOIN incentives i ON i.id = ib.incentive_id
            WHERE i.client_id = ? AND i.deleted = false
            AND i.status = 'ACTIVE'
            """);
        List<Object> params = new ArrayList<>();
        params.add(clientId);
        appendIncentiveTypeFilter(sql, params, types);

        return jdbcTemplate.queryForObject(sql.toString(), BigDecimal.class, params.toArray());
    }

    private BigDecimal getTotalUtilized(UUID clientId, List<IncentiveType> types, String region) {
        StringBuilder sql = new StringBuilder("""
            SELECT COALESCE(SUM(bu.utilized), 0)
            FROM budget_utilizations bu
            JOIN incentives i ON i.id = bu.incentive_id
            WHERE i.client_id = ? AND i.deleted = false
            AND i.status = 'ACTIVE'
            """);
        List<Object> params = new ArrayList<>();
        params.add(clientId);
        appendIncentiveTypeFilter(sql, params, types);

        if (region != null) {
            sql.append(" AND (bu.location_value_id = ?::uuid OR bu.location_value_id IS NULL)");
            params.add(region);
        }

        return jdbcTemplate.queryForObject(sql.toString(), BigDecimal.class, params.toArray());
    }

    // ── New Partner/User counts (created within a date range) ────────────────────

    private long countNewPartnerCompanies(UUID clientId, String region, DateRange range) {
        StringBuilder sql = new StringBuilder(
            "SELECT COUNT(*) FROM partner_companies WHERE client_id = ? AND status = 'ACTIVE'"
            + " AND created_at >= ? AND created_at < ?");
        List<Object> params = new ArrayList<>();
        params.add(clientId);
        params.add(range.start().atStartOfDay());
        params.add(range.end().plusDays(1).atStartOfDay());
        if (region != null) {
            sql.append(" AND region = ?");
            params.add(region);
        }
        return jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
    }

    private long countNewPartnerUsers(UUID clientId, String region, DateRange range) {
        StringBuilder sql = new StringBuilder(
            "SELECT COUNT(*) FROM users u WHERE u.client_id = ? AND u.status = 'ACTIVE'"
            + " AND u.created_at >= ? AND u.created_at < ?");
        List<Object> params = new ArrayList<>();
        params.add(clientId);
        params.add(range.start().atStartOfDay());
        params.add(range.end().plusDays(1).atStartOfDay());
        sql.append(" AND u.partner_company_id IN (SELECT id FROM partner_companies WHERE client_id = ? AND status = 'ACTIVE'");
        params.add(clientId);
        if (region != null) {
            sql.append(" AND region = ?");
            params.add(region);
        }
        sql.append(")");
        return jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
    }

    // ── Quarter-scoped budget queries ────────────────────────────────────────────

    private BigDecimal getTotalBudgetForQuarter(UUID clientId, List<IncentiveType> types,
                                                 String region, DateRange quarter) {
        StringBuilder sql = new StringBuilder("""
            SELECT COALESCE(SUM(ib.total_budget), 0)
            FROM incentive_budgets ib
            JOIN incentives i ON i.id = ib.incentive_id
            WHERE i.client_id = ? AND i.deleted = false
            AND i.status = 'ACTIVE'
            AND i.start_date <= ? AND i.end_date >= ?
            """);
        List<Object> params = new ArrayList<>();
        params.add(clientId);
        params.add(quarter.end());
        params.add(quarter.start());
        appendIncentiveTypeFilter(sql, params, types);
        return jdbcTemplate.queryForObject(sql.toString(), BigDecimal.class, params.toArray());
    }

    private BigDecimal getTotalUtilizedForQuarter(UUID clientId, List<IncentiveType> types,
                                                    String region, DateRange quarter) {
        StringBuilder sql = new StringBuilder("""
            SELECT COALESCE(SUM(bu.utilized), 0)
            FROM budget_utilizations bu
            JOIN incentives i ON i.id = bu.incentive_id
            WHERE i.client_id = ? AND i.deleted = false
            AND i.status = 'ACTIVE'
            AND i.start_date <= ? AND i.end_date >= ?
            """);
        List<Object> params = new ArrayList<>();
        params.add(clientId);
        params.add(quarter.end());
        params.add(quarter.start());
        appendIncentiveTypeFilter(sql, params, types);
        if (region != null) {
            sql.append(" AND (bu.location_value_id = ?::uuid OR bu.location_value_id IS NULL)");
            params.add(region);
        }
        return jdbcTemplate.queryForObject(sql.toString(), BigDecimal.class, params.toArray());
    }

    // ── Trend data builders ─────────────────────────────────────────────────────

    private List<TrendDataPoint> buildEnrollmentTrend(UUID clientId, String region, DateRange range,
                                                       String table, UUID partnerCompanyId) {
        String interval = trendInterval(range);

        // Get base count before the period
        long baseCount;
        if ("partner_companies".equals(table)) {
            baseCount = countPartnerCompaniesAsOf(clientId, region, range.start().minusDays(1));
        } else {
            baseCount = countPartnerUsersAsOf(clientId, region, partnerCompanyId, range.start().minusDays(1));
        }

        // Get new additions per interval
        StringBuilder sql = new StringBuilder(
            "SELECT date_trunc('" + interval + "', created_at)::date AS d, COUNT(*) FROM " + table
            + " WHERE client_id = ? AND created_at >= ? AND created_at < ?");
        List<Object> params = new ArrayList<>();
        params.add(clientId);
        params.add(range.start().atStartOfDay());
        params.add(range.end().plusDays(1).atStartOfDay());

        if ("partner_companies".equals(table)) {
            sql.append(" AND status = 'ACTIVE'");
            if (region != null) {
                sql.append(" AND region = ?");
                params.add(region);
            }
        } else {
            sql.append(" AND status = 'ACTIVE'");
            if (partnerCompanyId != null) {
                sql.append(" AND partner_company_id = ?");
                params.add(partnerCompanyId);
            } else {
                sql.append(" AND partner_company_id IN (SELECT id FROM partner_companies WHERE client_id = ? AND status = 'ACTIVE'");
                params.add(clientId);
                if (region != null) {
                    sql.append(" AND region = ?");
                    params.add(region);
                }
                sql.append(")");
            }
        }
        sql.append(" GROUP BY 1 ORDER BY 1");

        Map<LocalDate, Long> additions = new LinkedHashMap<>();
        jdbcTemplate.query(sql.toString(), params.toArray(), rs -> {
            additions.put(rs.getDate(1).toLocalDate(), rs.getLong(2));
        });

        return buildCumulativeTrend(range, interval, baseCount, additions);
    }

    private List<TrendDataPoint> buildCompaniesEarningTrend(UUID clientId, String region, DateRange range) {
        String interval = trendInterval(range);
        StringBuilder sql = new StringBuilder("""
            SELECT date_trunc('""" + interval + """
            ', rt.created_at)::date AS d, COUNT(DISTINCT pc.id)
            FROM partner_companies pc
            JOIN users u ON u.partner_company_id = pc.id AND u.client_id = pc.client_id
            JOIN reward_transactions rt ON rt.user_id = u.id AND rt.client_id = u.client_id
            WHERE pc.client_id = ? AND pc.status = 'ACTIVE'
            AND u.status = 'ACTIVE'
            AND rt.created_at >= ? AND rt.created_at < ?
            """);
        List<Object> params = new ArrayList<>();
        params.add(clientId);
        params.add(range.start().atStartOfDay());
        params.add(range.end().plusDays(1).atStartOfDay());
        if (region != null) {
            sql.append(" AND pc.id IN (SELECT partner_company_id FROM partner_company_locations WHERE location_value_id = ?::uuid)");
            params.add(region);
        }
        sql.append(" GROUP BY 1 ORDER BY 1");

        return queryIntervalTrend(sql.toString(), params, range, interval);
    }

    private List<TrendDataPoint> buildUsersEarningTrend(UUID clientId, UUID partnerCompanyId, DateRange range) {
        String interval = trendInterval(range);
        String sql = """
            SELECT date_trunc('""" + interval + """
            ', rt.created_at)::date AS d, COUNT(DISTINCT rt.user_id)
            FROM reward_transactions rt
            JOIN users u ON u.id = rt.user_id AND u.client_id = rt.client_id
            WHERE rt.client_id = ? AND u.partner_company_id = ?
            AND u.status = 'ACTIVE'
            AND rt.created_at >= ? AND rt.created_at < ?
            GROUP BY 1 ORDER BY 1
            """;
        List<Object> params = List.of(clientId, partnerCompanyId,
            range.start().atStartOfDay(), range.end().plusDays(1).atStartOfDay());

        return queryIntervalTrend(sql, new ArrayList<>(params), range, interval);
    }

    private List<TrendDataPoint> buildClaimsTrend(UUID clientId, UUID partnerCompanyId, DateRange range) {
        String interval = trendInterval(range);
        String sql = """
            SELECT date_trunc('""" + interval + """
            ', ca.claimed_at)::date AS d, COUNT(*)
            FROM claim_actions ca
            JOIN purchase_orders po ON po.id = ca.purchase_order_id
            WHERE ca.client_id = ? AND po.partner_company_id = ?
            AND ca.claimed_at >= ? AND ca.claimed_at < ?
            GROUP BY 1 ORDER BY 1
            """;
        List<Object> params = List.of(clientId, partnerCompanyId,
            range.start().atStartOfDay(), range.end().plusDays(1).atStartOfDay());

        return queryIntervalTrend(sql, new ArrayList<>(params), range, interval);
    }

    private List<TrendDataPoint> buildRewardsTrend(UUID clientId, String region, UUID partnerCompanyId,
                                                     List<IncentiveType> types, DateRange range) {
        String interval = trendInterval(range);
        StringBuilder sql = new StringBuilder("""
            SELECT date_trunc('""" + interval + """
            ', rt.created_at)::date AS d, COALESCE(SUM(rt.amount_awarded), 0)
            FROM reward_transactions rt
            JOIN incentives i ON i.id = rt.incentive_id
            JOIN users u ON u.id = rt.user_id AND u.client_id = rt.client_id
            WHERE rt.client_id = ? AND rt.created_at >= ? AND rt.created_at < ?
            AND i.deleted = false AND rt.currency_id IN ('cash', 'points')
            """);
        List<Object> params = new ArrayList<>();
        params.add(clientId);
        params.add(range.start().atStartOfDay());
        params.add(range.end().plusDays(1).atStartOfDay());

        appendIncentiveTypeFilter(sql, params, types);
        appendRegionFilter(sql, params, region, clientId);
        appendPartnerFilter(sql, params, partnerCompanyId);
        sql.append(" GROUP BY 1 ORDER BY 1");

        return queryIntervalTrend(sql.toString(), params, range, interval);
    }

    private List<TrendDataPoint> buildBudgetTrend(UUID clientId, String region, UUID partnerCompanyId,
                                                    List<IncentiveType> types, DateRange range, BigDecimal totalBudget) {
        // Build cumulative utilization trend based on reward transactions within the period
        List<TrendDataPoint> rewardsTrend = buildRewardsTrend(clientId, region, partnerCompanyId, types, range);
        if (totalBudget.compareTo(BigDecimal.ZERO) == 0) return rewardsTrend;

        // Convert absolute amounts to percentages of budget
        BigDecimal cumulative = BigDecimal.ZERO;
        List<TrendDataPoint> result = new ArrayList<>();
        for (TrendDataPoint point : rewardsTrend) {
            cumulative = cumulative.add(point.value());
            BigDecimal percent = cumulative.multiply(BigDecimal.valueOf(100))
                .divide(totalBudget, 0, RoundingMode.HALF_UP);
            result.add(new TrendDataPoint(point.label(), percent));
        }
        return result;
    }

    private List<TrendDataPoint> buildUsersParticipatingTrend(UUID clientId, String region, UUID partnerCompanyId,
                                                                List<IncentiveType> types, DateRange range, long totalUsers) {
        String interval = trendInterval(range);
        StringBuilder sql = new StringBuilder("""
            SELECT date_trunc('""" + interval + """
            ', rt.created_at)::date AS d, COUNT(DISTINCT rt.user_id)
            FROM reward_transactions rt
            JOIN incentives i ON i.id = rt.incentive_id
            JOIN users u ON u.id = rt.user_id AND u.client_id = rt.client_id
            WHERE rt.client_id = ? AND rt.created_at >= ? AND rt.created_at < ?
            AND i.deleted = false
            """);
        List<Object> params = new ArrayList<>();
        params.add(clientId);
        params.add(range.start().atStartOfDay());
        params.add(range.end().plusDays(1).atStartOfDay());

        appendIncentiveTypeFilter(sql, params, types);
        appendRegionFilter(sql, params, region, clientId);
        appendPartnerFilter(sql, params, partnerCompanyId);
        sql.append(" GROUP BY 1 ORDER BY 1");

        List<TrendDataPoint> raw = queryIntervalTrend(sql.toString(), params, range, interval);
        if (totalUsers == 0) return raw;

        // Convert to percentages
        return raw.stream().map(p -> new TrendDataPoint(p.label(),
            p.value().multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(totalUsers), 0, RoundingMode.HALF_UP))
        ).toList();
    }

    // ── Shared helpers ──────────────────────────────────────────────────────────

    private void appendIncentiveTypeFilter(StringBuilder sql, List<Object> params, List<IncentiveType> types) {
        if (types.size() < IncentiveType.values().length) {
            sql.append(" AND i.incentive_type IN (");
            for (int idx = 0; idx < types.size(); idx++) {
                sql.append(idx > 0 ? ", ?" : "?");
                params.add(types.get(idx).name());
            }
            sql.append(")");
        }
    }

    private void appendRegionFilter(StringBuilder sql, List<Object> params, String locationValueId, UUID clientId) {
        sql.append(" AND u.status = 'ACTIVE' AND u.partner_company_id IN (SELECT id FROM partner_companies WHERE client_id = ? AND status = 'ACTIVE'");
        params.add(clientId);
        if (locationValueId != null) {
            sql.append(" AND id IN (SELECT partner_company_id FROM partner_company_locations WHERE location_value_id = ?::uuid)");
            params.add(locationValueId);
        }
        sql.append(")");
    }

    private void appendPartnerFilter(StringBuilder sql, List<Object> params, UUID partnerCompanyId) {
        if (partnerCompanyId != null) {
            sql.append(" AND u.partner_company_id = ?");
            params.add(partnerCompanyId);
        }
    }

    private List<TrendDataPoint> queryIntervalTrend(String sql, List<Object> params, DateRange range, String interval) {
        Map<LocalDate, BigDecimal> dataMap = new LinkedHashMap<>();
        jdbcTemplate.query(sql, params.toArray(), rs -> {
            dataMap.put(rs.getDate(1).toLocalDate(), rs.getBigDecimal(2));
        });

        // Fill in all intervals with zero where there's no data
        List<TrendDataPoint> result = new ArrayList<>();
        LocalDate cursor = range.start();
        while (!cursor.isAfter(range.end())) {
            BigDecimal val = dataMap.getOrDefault(cursor, BigDecimal.ZERO);
            // Find nearest data point for this interval
            for (Map.Entry<LocalDate, BigDecimal> entry : dataMap.entrySet()) {
                if (isSameInterval(cursor, entry.getKey(), interval)) {
                    val = entry.getValue();
                    break;
                }
            }
            result.add(new TrendDataPoint(formatLabel(interval, cursor), val));
            cursor = advanceCursor(cursor, interval);
        }
        return result;
    }

    private List<TrendDataPoint> buildCumulativeTrend(DateRange range, String interval,
                                                        long baseCount, Map<LocalDate, Long> additions) {
        List<TrendDataPoint> result = new ArrayList<>();
        long running = baseCount;
        LocalDate cursor = range.start();
        while (!cursor.isAfter(range.end())) {
            // Sum additions for this interval
            for (Map.Entry<LocalDate, Long> entry : additions.entrySet()) {
                if (isSameInterval(cursor, entry.getKey(), interval)) {
                    running += entry.getValue();
                }
            }
            result.add(new TrendDataPoint(formatLabel(interval, cursor), BigDecimal.valueOf(running)));
            cursor = advanceCursor(cursor, interval);
        }
        return result;
    }

    private boolean isSameInterval(LocalDate cursor, LocalDate date, String interval) {
        return switch (interval) {
            case "day" -> cursor.equals(date);
            case "week" -> cursor.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) == date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
                && cursor.getYear() == date.getYear();
            case "month" -> YearMonth.from(cursor).equals(YearMonth.from(date));
            default -> false;
        };
    }

    private LocalDate advanceCursor(LocalDate cursor, String interval) {
        return switch (interval) {
            case "day" -> cursor.plusDays(1);
            case "week" -> cursor.plusWeeks(1);
            case "month" -> cursor.plusMonths(1);
            default -> cursor.plusDays(1);
        };
    }

    private BigDecimal trendPercent(long current, long previous) {
        return trendPercent(BigDecimal.valueOf(current), BigDecimal.valueOf(previous));
    }

    private BigDecimal trendPercent(BigDecimal current, BigDecimal previous) {
        if (previous.compareTo(BigDecimal.ZERO) == 0) {
            return current.compareTo(BigDecimal.ZERO) > 0 ? BigDecimal.valueOf(100) : BigDecimal.ZERO;
        }
        return current.subtract(previous)
            .multiply(BigDecimal.valueOf(100))
            .divide(previous, 0, RoundingMode.HALF_UP);
    }

    private static final CurrencyAggregates ZERO_AGGREGATES = new CurrencyAggregates(BigDecimal.ZERO, 0);

    private HomeRewardBreakdownData buildRewardBreakdown(UUID clientId,
                                                           Map<String, CurrencyAggregates> currencyTotals,
                                                           BigDecimal totalMonetary) {
        Map<String, HomeRewardBreakdownData.CurrencyAmount> monetary = new LinkedHashMap<>();
        Map<String, HomeRewardBreakdownData.CurrencyCount> nonMonetary = new LinkedHashMap<>();

        List<Currency> currencies = currencyRepository.findByClientIdOrderByTypeAscCodeAsc(clientId);

        // Compute monetary amounts and percentages
        BigDecimal allocatedPercent = BigDecimal.ZERO;
        List<Currency> monetaryList = currencies.stream()
            .filter(c -> c.getType() == CurrencyType.MONETARY).toList();
        for (int i = 0; i < monetaryList.size(); i++) {
            Currency c = monetaryList.get(i);
            BigDecimal amount = currencyTotals.getOrDefault(c.getCode(), ZERO_AGGREGATES).sum();
            BigDecimal percent;
            if (totalMonetary.compareTo(BigDecimal.ZERO) > 0) {
                if (i == monetaryList.size() - 1) {
                    // Last monetary currency gets the remainder to ensure percentages sum to 100
                    percent = BigDecimal.valueOf(100).subtract(allocatedPercent);
                } else {
                    percent = amount.multiply(BigDecimal.valueOf(100))
                        .divide(totalMonetary, 0, RoundingMode.HALF_UP);
                    allocatedPercent = allocatedPercent.add(percent);
                }
            } else {
                percent = BigDecimal.ZERO;
            }
            monetary.put(c.getCode(), new HomeRewardBreakdownData.CurrencyAmount(amount, percent));
        }

        // Non-monetary currencies
        currencies.stream()
            .filter(c -> c.getType() == CurrencyType.NON_MONETARY)
            .forEach(c -> nonMonetary.put(c.getCode(), new HomeRewardBreakdownData.CurrencyCount(
                currencyTotals.getOrDefault(c.getCode(), ZERO_AGGREGATES).count())));

        return new HomeRewardBreakdownData(monetary, nonMonetary);
    }

    // ── Fiscal quarter resolution ────────────────────────────────────────────────

    public record FiscalQuarter(LocalDate start, LocalDate end, String label) {
        public DateRange toDateRange() {
            return new DateRange(start, end);
        }
    }

    /**
     * Resolve the fiscal quarter containing the given date, returning start, end, and label.
     * Falls back to calendar quarter if no fiscal config exists.
     */
    private FiscalQuarter resolveCurrentFiscalQuarter(LocalDate date) {
        try {
            UUID clientId = tenantValidator.getCurrentClientId();
            List<FiscalYearConfig> configs = fiscalYearConfigRepository
                .findByClientIdOrderByStartDateAsc(clientId);
            for (FiscalYearConfig c : configs) {
                if (!date.isBefore(c.getStartDate()) && !date.isAfter(c.getEndDate())) {
                    if (!date.isBefore(c.getQ4StartDate())) {
                        return new FiscalQuarter(c.getQ4StartDate(), c.getQ4EndDate(), "Q4 " + c.getLabel());
                    }
                    if (!date.isBefore(c.getQ3StartDate())) {
                        return new FiscalQuarter(c.getQ3StartDate(), c.getQ3EndDate(), "Q3 " + c.getLabel());
                    }
                    if (!date.isBefore(c.getQ2StartDate())) {
                        return new FiscalQuarter(c.getQ2StartDate(), c.getQ2EndDate(), "Q2 " + c.getLabel());
                    }
                    return new FiscalQuarter(c.getQ1StartDate(), c.getQ1EndDate(), "Q1 " + c.getLabel());
                }
            }
        } catch (Exception e) {
            log.debug("Could not resolve fiscal quarter, falling back to calendar: {}", e.getMessage());
        }
        // Calendar fallback
        int quarter = (date.getMonthValue() - 1) / 3;
        LocalDate qStart = LocalDate.of(date.getYear(), quarter * 3 + 1, 1);
        LocalDate qEnd = qStart.plusMonths(3).minusDays(1);
        return new FiscalQuarter(qStart, qEnd, "Q" + (quarter + 1) + " CY" + date.getYear());
    }

    /**
     * Returns [twoQuartersAgo, oneQuarterAgo, currentQuarter].
     */
    private List<FiscalQuarter> resolveThreeQuarters() {
        FiscalQuarter current = resolveCurrentFiscalQuarter(LocalDate.now());
        FiscalQuarter prev = resolveCurrentFiscalQuarter(current.start().minusDays(1));
        FiscalQuarter twoAgo = resolveCurrentFiscalQuarter(prev.start().minusDays(1));
        return List.of(twoAgo, prev, current);
    }

    // ── Quarterly trend helpers ──────────────────────────────────────────────────

    @FunctionalInterface
    private interface QuarterMetricFunction {
        BigDecimal apply(DateRange range);
    }

    private List<TrendDataPoint> buildMonthlyTrend(List<FiscalQuarter> quarters,
                                                     QuarterMetricFunction metricFn) {
        List<TrendDataPoint> points = new ArrayList<>();
        LocalDate today = LocalDate.now();
        DateTimeFormatter labelFmt = DateTimeFormatter.ofPattern("MMM yy");
        for (FiscalQuarter q : quarters) {
            BigDecimal cumulative = BigDecimal.ZERO; // Reset at each quarter boundary
            LocalDate monthStart = q.start().withDayOfMonth(1);
            while (!monthStart.isAfter(q.end()) && !monthStart.isAfter(today)) {
                LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);
                if (monthEnd.isAfter(q.end())) monthEnd = q.end();
                if (monthEnd.isAfter(today)) monthEnd = today;
                DateRange monthRange = new DateRange(monthStart, monthEnd);
                cumulative = cumulative.add(metricFn.apply(monthRange));
                points.add(new TrendDataPoint(monthStart.format(labelFmt), cumulative));
                monthStart = monthStart.plusMonths(1);
            }
        }
        return points;
    }

    // ── Program Performance (combined endpoint) ──────────────────────────────────

    @Transactional(readOnly = true)
    public ProgramPerformanceResponse getProgramPerformance(String region, UUID partnerCompanyId) {
        try {
            return buildProgramPerformance(region, partnerCompanyId);
        } catch (Exception e) {
            log.error("Failed to build program performance metrics", e);
            throw e;
        }
    }

    private ProgramPerformanceResponse buildProgramPerformance(String region, UUID partnerCompanyId) {
        UUID clientId = tenantValidator.getCurrentClientId();
        List<FiscalQuarter> quarters = resolveThreeQuarters();
        FiscalQuarter currentQ = quarters.get(2);
        FiscalQuarter previousQ = quarters.get(1);

        // Days-based comparison: cap current quarter at today, compare same day-count into previous
        LocalDate today = LocalDate.now();
        long daysElapsed = ChronoUnit.DAYS.between(currentQ.start(), today) + 1;
        DateRange current = new DateRange(currentQ.start(), today);
        LocalDate prevCap = previousQ.start().plusDays(daysElapsed - 1);
        if (prevCap.isAfter(previousQ.end())) prevCap = previousQ.end();
        DateRange previous = new DateRange(previousQ.start(), prevCap);
        boolean isGlobal = region == null || "GLOBAL".equalsIgnoreCase(region);
        String regionParam = isGlobal ? null : region;
        List<IncentiveType> types = List.of(IncentiveType.values());

        Set<String> monetaryCodes = currencyRepository.findByClientIdAndType(clientId, CurrencyType.MONETARY)
            .stream().map(Currency::getCode).collect(Collectors.toSet());

        // ── Incentive Performance ────────────────────────────────────────────────

        // 1. Total Rewards Earned
        Map<String, CurrencyAggregates> currencyTotals = getRewardsByCurrency(
            clientId, regionParam, partnerCompanyId, types, current);
        BigDecimal totalMonetary = currencyTotals.entrySet().stream()
            .filter(e -> monetaryCodes.contains(e.getKey()))
            .map(e -> e.getValue().sum())
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, CurrencyAggregates> prevCurrencyTotals = getRewardsByCurrency(
            clientId, regionParam, partnerCompanyId, types, previous);
        BigDecimal prevTotalMonetary = prevCurrencyTotals.entrySet().stream()
            .filter(e -> monetaryCodes.contains(e.getKey()))
            .map(e -> e.getValue().sum())
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<TrendDataPoint> rewardsTrend = buildMonthlyTrend(quarters, range -> {
            Map<String, CurrencyAggregates> ct = getRewardsByCurrency(
                clientId, regionParam, partnerCompanyId, types, range);
            return ct.entrySet().stream()
                .filter(e -> monetaryCodes.contains(e.getKey()))
                .map(e -> e.getValue().sum())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        });
        MetricResponse rewardsMetric = new MetricResponse(
            totalMonetary, null,
            trendPercent(totalMonetary, prevTotalMonetary), rewardsTrend);

        HomeRewardBreakdownData breakdown = buildRewardBreakdown(clientId, currencyTotals, totalMonetary);

        // 2. Budget Utilized — scoped to incentives running in the current quarter
        BigDecimal totalBudget = getTotalBudgetForQuarter(clientId, types, regionParam, current);
        BigDecimal totalUtilized = getTotalUtilizedForQuarter(clientId, types, regionParam, current);
        BigDecimal budgetPercent = totalBudget.compareTo(BigDecimal.ZERO) > 0
            ? totalUtilized.multiply(BigDecimal.valueOf(100)).divide(totalBudget, 0, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        List<TrendDataPoint> budgetTrend = buildMonthlyTrend(quarters, range -> {
            BigDecimal qBudget = getTotalBudgetForQuarter(clientId, types, regionParam, range);
            BigDecimal qUtilized = getTotalUtilizedForQuarter(clientId, types, regionParam, range);
            return qBudget.compareTo(BigDecimal.ZERO) > 0
                ? qUtilized.multiply(BigDecimal.valueOf(100)).divide(qBudget, 0, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        });
        MetricResponse budgetMetric = new MetricResponse(
            budgetPercent,
            "of $" + totalBudget.setScale(0, RoundingMode.HALF_UP).toPlainString(),
            null, budgetTrend);

        // 3. Users Participating
        long totalUsers = countPartnerUsers(clientId, regionParam, partnerCompanyId);
        long participatingCurrent = countUsersParticipating(clientId, regionParam, partnerCompanyId, types, current);
        long participatingPrev = countUsersParticipating(clientId, regionParam, partnerCompanyId, types, previous);
        BigDecimal participatingPercent = totalUsers > 0
            ? BigDecimal.valueOf(participatingCurrent * 100).divide(BigDecimal.valueOf(totalUsers), 0, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        List<TrendDataPoint> participatingTrend = buildMonthlyTrend(quarters, range -> {
            long count = countUsersParticipating(clientId, regionParam, partnerCompanyId, types, range);
            return totalUsers > 0
                ? BigDecimal.valueOf(count * 100).divide(BigDecimal.valueOf(totalUsers), 0, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        });
        MetricResponse participatingMetric = new MetricResponse(
            participatingPercent, null,
            trendPercent(participatingCurrent, participatingPrev), participatingTrend);

        // ── Participation Metrics ────────────────────────────────────────────────

        boolean partnerFiltered = partnerCompanyId != null;
        MetricResponse partnerCompaniesEnrolled = null;
        MetricResponse partnerUsersEnrolled = null;
        MetricResponse companiesEarningRewards = null;
        MetricResponse partnerEnrolledUsers = null;
        MetricResponse usersEarningRewards = null;
        MetricResponse userClaimsMade = null;

        if (partnerFiltered) {
            // Partner Users Enrolled (new users for this partner during quarter)
            long usersCurrent = countNewPartnerUsers(clientId, regionParam, current);
            long usersPrev = countNewPartnerUsers(clientId, regionParam, previous);
            List<TrendDataPoint> usersTrend = buildMonthlyTrend(quarters, range ->
                BigDecimal.valueOf(countNewPartnerUsers(clientId, regionParam, range)));
            partnerEnrolledUsers = new MetricResponse(
                BigDecimal.valueOf(usersCurrent), null,
                trendPercent(usersCurrent, usersPrev), usersTrend);

            // Users Earning Rewards
            long totalPartnerUsers = countPartnerUsers(clientId, regionParam, partnerCompanyId);
            long usersEarning = countUsersEarningForPartner(clientId, partnerCompanyId, current);
            long usersEarningPrev = countUsersEarningForPartner(clientId, partnerCompanyId, previous);
            BigDecimal earningPercent = totalPartnerUsers > 0
                ? BigDecimal.valueOf(usersEarning * 100).divide(BigDecimal.valueOf(totalPartnerUsers), 0, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
            List<TrendDataPoint> earningTrend = buildMonthlyTrend(quarters, range -> {
                long ue = countUsersEarningForPartner(clientId, partnerCompanyId, range);
                long total = countPartnerUsers(clientId, regionParam, partnerCompanyId);
                return total > 0
                    ? BigDecimal.valueOf(ue * 100).divide(BigDecimal.valueOf(total), 0, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            });
            usersEarningRewards = new MetricResponse(
                earningPercent,
                "(" + usersEarning + " of " + totalPartnerUsers + " users)",
                trendPercent(usersEarning, usersEarningPrev), earningTrend);

            // User Claims Made
            long claimsCurrent = countClaimsForPartner(clientId, partnerCompanyId, current);
            long claimsPrev = countClaimsForPartner(clientId, partnerCompanyId, previous);
            List<TrendDataPoint> claimsTrend = buildMonthlyTrend(quarters, range ->
                BigDecimal.valueOf(countClaimsForPartner(clientId, partnerCompanyId, range)));
            userClaimsMade = new MetricResponse(
                BigDecimal.valueOf(claimsCurrent), null,
                trendPercent(claimsCurrent, claimsPrev), claimsTrend);
        } else {
            // New Partners (created during current quarter)
            long newCompaniesCurrent = countNewPartnerCompanies(clientId, regionParam, current);
            long newCompaniesPrev = countNewPartnerCompanies(clientId, regionParam, previous);
            List<TrendDataPoint> companiesTrend = buildMonthlyTrend(quarters, range ->
                BigDecimal.valueOf(countNewPartnerCompanies(clientId, regionParam, range)));
            partnerCompaniesEnrolled = new MetricResponse(
                BigDecimal.valueOf(newCompaniesCurrent), null,
                trendPercent(newCompaniesCurrent, newCompaniesPrev), companiesTrend);

            // New Users (created during current quarter)
            long newUsersCurrent = countNewPartnerUsers(clientId, regionParam, current);
            long newUsersPrev = countNewPartnerUsers(clientId, regionParam, previous);
            List<TrendDataPoint> usersTrend = buildMonthlyTrend(quarters, range ->
                BigDecimal.valueOf(countNewPartnerUsers(clientId, regionParam, range)));
            partnerUsersEnrolled = new MetricResponse(
                BigDecimal.valueOf(newUsersCurrent), null,
                trendPercent(newUsersCurrent, newUsersPrev), usersTrend);

            // Companies Earning Rewards
            long companiesEarning = countCompaniesEarning(clientId, regionParam, current);
            long companiesEarningPrev = countCompaniesEarning(clientId, regionParam, previous);
            long totalCompanies = countPartnerCompanies(clientId, regionParam);
            BigDecimal earningPercent = totalCompanies > 0
                ? BigDecimal.valueOf(companiesEarning * 100).divide(BigDecimal.valueOf(totalCompanies), 0, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
            List<TrendDataPoint> earningTrend = buildMonthlyTrend(quarters, range -> {
                long ce = countCompaniesEarning(clientId, regionParam, range);
                long tc = countPartnerCompanies(clientId, regionParam);
                return tc > 0
                    ? BigDecimal.valueOf(ce * 100).divide(BigDecimal.valueOf(tc), 0, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            });
            companiesEarningRewards = new MetricResponse(
                earningPercent,
                "(" + companiesEarning + " of " + totalCompanies + " companies)",
                trendPercent(companiesEarning, companiesEarningPrev), earningTrend);
        }

        return new ProgramPerformanceResponse(
            rewardsMetric, budgetMetric, participatingMetric,
            breakdown, totalBudget,
            partnerFiltered,
            partnerCompaniesEnrolled, partnerUsersEnrolled, companiesEarningRewards,
            partnerEnrolledUsers, usersEarningRewards, userClaimsMade,
            currentQ.label()
        );
    }
}
