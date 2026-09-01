package com.tenxengage.app.service;

import com.tenxengage.app.dto.response.AnnualRewardSummaryResponse;
import com.tenxengage.app.dto.response.EmployerBikReportResponse;
import com.tenxengage.app.entity.Currency;
import com.tenxengage.app.entity.enums.CurrencyType;
import com.tenxengage.app.repository.CurrencyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TaxReportingService {

    private static final Logger log = LoggerFactory.getLogger(TaxReportingService.class);

    private final JdbcTemplate jdbcTemplate;
    private final CurrencyRepository currencyRepository;

    public TaxReportingService(JdbcTemplate jdbcTemplate,
                                CurrencyRepository currencyRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.currencyRepository = currencyRepository;
    }

    /**
     * Returns per-user, per-currency reward summaries for a given client and year,
     * sourced from the user_annual_reward_summary database view.
     */
    @Transactional(readOnly = true)
    public List<AnnualRewardSummaryResponse> getUserAnnualRewardSummary(UUID clientId, int year) {
        log.info("Generating annual reward summary: clientId={}, year={}", clientId, year);

        String sql = """
            SELECT user_id, first_name, last_name, email, country_code,
                   partner_company_name, currency_id, reward_year::int,
                   total_awarded, transaction_count
            FROM user_annual_reward_summary
            WHERE client_id = ? AND reward_year = ?
            ORDER BY last_name, first_name, currency_id
            """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new AnnualRewardSummaryResponse(
                UUID.fromString(rs.getString("user_id")),
                rs.getString("first_name"),
                rs.getString("last_name"),
                rs.getString("email"),
                rs.getString("country_code"),
                rs.getString("partner_company_name"),
                rs.getString("currency_id"),
                rs.getInt("reward_year"),
                rs.getBigDecimal("total_awarded"),
                rs.getLong("transaction_count")
        ), clientId, year);
    }

    /**
     * Generates an employer-level BIK report for a specific partner company and year.
     * Aggregates rewards per employee across all currencies and calculates USD equivalents.
     */
    @Transactional(readOnly = true)
    public EmployerBikReportResponse getEmployerBikReport(UUID partnerCompanyId, int year) {
        log.info("Generating employer BIK report: partnerCompanyId={}, year={}",
                partnerCompanyId, year);

        String sql = """
            SELECT s.client_id, s.user_id, s.first_name, s.last_name, s.email,
                   s.country_code, s.partner_company_name, s.currency_id, s.total_awarded
            FROM user_annual_reward_summary s
            JOIN users u ON u.id = s.user_id
            WHERE u.partner_company_id = ? AND s.reward_year = ?
            ORDER BY s.last_name, s.first_name, s.currency_id
            """;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, partnerCompanyId, year);

        if (rows.isEmpty()) {
            return new EmployerBikReportResponse(null, year, List.of());
        }

        // Build a lookup of conversion rates from the first row's client_id
        UUID clientId = UUID.fromString(rows.get(0).get("client_id").toString());
        Map<String, BigDecimal> conversionRates = loadConversionRates(clientId);

        // Aggregate by employee (user_id)
        Map<String, EmployeeAccumulator> employeeMap = new LinkedHashMap<>();
        String partnerCompanyName = null;

        for (Map<String, Object> row : rows) {
            String userId = row.get("user_id").toString();
            if (partnerCompanyName == null) {
                partnerCompanyName = (String) row.get("partner_company_name");
            }

            EmployeeAccumulator acc = employeeMap.computeIfAbsent(userId, k ->
                    new EmployeeAccumulator(
                            (String) row.get("first_name"),
                            (String) row.get("last_name"),
                            (String) row.get("email"),
                            (String) row.get("country_code")));

            String currencyId = (String) row.get("currency_id");
            BigDecimal totalAwarded = (BigDecimal) row.get("total_awarded");
            acc.rewardsByCurrency.put(currencyId, totalAwarded);

            BigDecimal rate = conversionRates.get(currencyId);
            if (rate != null && rate.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal usdValue = totalAwarded.divide(rate, 2, RoundingMode.HALF_UP);
                acc.totalUsdEquivalent = acc.totalUsdEquivalent.add(usdValue);
            }
        }

        List<EmployerBikReportResponse.EmployeeReward> employees = employeeMap.values().stream()
                .map(acc -> new EmployerBikReportResponse.EmployeeReward(
                        acc.firstName, acc.lastName, acc.email, acc.countryCode,
                        acc.rewardsByCurrency, acc.totalUsdEquivalent))
                .toList();

        return new EmployerBikReportResponse(partnerCompanyName, year, employees);
    }

    /**
     * Exports tax report data in a structured format suitable for CSV or JSON conversion.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> exportTaxReport(UUID clientId, int year, String format) {
        log.info("Exporting tax report: clientId={}, year={}, format={}", clientId, year, format);

        List<AnnualRewardSummaryResponse> summaries = getUserAnnualRewardSummary(clientId, year);
        Map<String, BigDecimal> conversionRates = loadConversionRates(clientId);

        List<Map<String, Object>> exportRows = new ArrayList<>();
        for (AnnualRewardSummaryResponse summary : summaries) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", summary.userId().toString());
            row.put("firstName", summary.firstName());
            row.put("lastName", summary.lastName());
            row.put("email", summary.email());
            row.put("countryCode", summary.countryCode());
            row.put("partnerCompanyName", summary.partnerCompanyName());
            row.put("currencyId", summary.currencyId());
            row.put("year", summary.year());
            row.put("totalAwarded", summary.totalAwarded());
            row.put("transactionCount", summary.transactionCount());

            BigDecimal rate = conversionRates.get(summary.currencyId());
            if (rate != null && rate.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal usdEquivalent = summary.totalAwarded()
                        .divide(rate, 2, RoundingMode.HALF_UP);
                row.put("usdEquivalent", usdEquivalent);
            } else {
                row.put("usdEquivalent", null);
            }

            exportRows.add(row);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("clientId", clientId.toString());
        result.put("year", year);
        result.put("format", format);
        result.put("generatedAt", java.time.Instant.now().toString());
        result.put("recordCount", exportRows.size());
        result.put("conversionRates", conversionRates);
        result.put("records", exportRows);

        return result;
    }

    private Map<String, BigDecimal> loadConversionRates(UUID clientId) {
        Map<String, BigDecimal> rates = new LinkedHashMap<>();
        List<Currency> currencies = currencyRepository
                .findByClientIdAndType(clientId, CurrencyType.MONETARY);
        for (Currency currency : currencies) {
            if (currency.getConversionRate() != null) {
                rates.put(currency.getCode(), currency.getConversionRate());
            }
        }
        return rates;
    }

    private static class EmployeeAccumulator {
        final String firstName;
        final String lastName;
        final String email;
        final String countryCode;
        final Map<String, BigDecimal> rewardsByCurrency = new LinkedHashMap<>();
        BigDecimal totalUsdEquivalent = BigDecimal.ZERO;

        EmployeeAccumulator(String firstName, String lastName, String email, String countryCode) {
            this.firstName = firstName;
            this.lastName = lastName;
            this.email = email;
            this.countryCode = countryCode;
        }
    }
}
