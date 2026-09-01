package com.tenxengage.app.service;

import com.tenxengage.app.dto.response.AnnualRewardSummaryResponse;
import com.tenxengage.app.dto.response.EmployerBikReportResponse;
import com.tenxengage.app.entity.Currency;
import com.tenxengage.app.entity.enums.CurrencyType;
import com.tenxengage.app.repository.CurrencyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaxReportingServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private CurrencyRepository currencyRepository;

    @InjectMocks
    private TaxReportingService taxReportingService;

    private UUID clientId;
    private UUID partnerCompanyId;

    @BeforeEach
    void setUp() {
        clientId = UUID.randomUUID();
        partnerCompanyId = UUID.randomUUID();
    }

    @Test
    @SuppressWarnings("unchecked")
    void getUserAnnualRewardSummary_returnsResults() {
        UUID userId = UUID.randomUUID();
        AnnualRewardSummaryResponse response = new AnnualRewardSummaryResponse(
                userId, "Jane", "Doe", "jane@example.com", "US",
                "Acme Corp", "CASH", 2025, new BigDecimal("5000.00"), 12L);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(clientId), eq(2025)))
                .thenReturn(List.of(response));

        List<AnnualRewardSummaryResponse> result = taxReportingService
                .getUserAnnualRewardSummary(clientId, 2025);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).firstName()).isEqualTo("Jane");
        assertThat(result.get(0).totalAwarded()).isEqualByComparingTo(new BigDecimal("5000.00"));
        assertThat(result.get(0).currencyId()).isEqualTo("CASH");
    }

    @Test
    @SuppressWarnings("unchecked")
    void getEmployerBikReport_aggregatesByEmployee() {
        UUID userId = UUID.randomUUID();
        Map<String, Object> row = new HashMap<>();
        row.put("client_id", clientId.toString());
        row.put("user_id", userId.toString());
        row.put("first_name", "John");
        row.put("last_name", "Smith");
        row.put("email", "john@example.com");
        row.put("country_code", "GB");
        row.put("partner_company_name", "Partner Inc");
        row.put("currency_id", "CASH");
        row.put("total_awarded", new BigDecimal("2500.00"));

        Currency cashCurrency = Currency.builder()
                .clientId(clientId)
                .code("CASH")
                .name("Cash")
                .type(CurrencyType.MONETARY)
                .conversionRate(BigDecimal.ONE)
                .build();

        when(jdbcTemplate.queryForList(anyString(), eq(partnerCompanyId), eq(2025)))
                .thenReturn(List.of(row));
        when(currencyRepository.findByClientIdAndType(clientId, CurrencyType.MONETARY))
                .thenReturn(List.of(cashCurrency));

        EmployerBikReportResponse result = taxReportingService
                .getEmployerBikReport(partnerCompanyId, 2025);

        assertThat(result).isNotNull();
        assertThat(result.partnerCompanyName()).isEqualTo("Partner Inc");
        assertThat(result.year()).isEqualTo(2025);
        assertThat(result.employees()).hasSize(1);
        assertThat(result.employees().get(0).firstName()).isEqualTo("John");
        assertThat(result.employees().get(0).totalUsdEquivalent())
                .isEqualByComparingTo(new BigDecimal("2500.00"));
    }
}
