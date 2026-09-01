package com.tenxengage.app.service.forecast;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.services.blocking.MessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForecastAiServiceTest {

    @Mock
    private AnthropicClient anthropicClient;

    @Mock
    private MessageService messageService;

    private ForecastAiService aiService;
    private ForecastBaselineConfig baselineConfig;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        baselineConfig = new ForecastBaselineConfig();
        aiService = new ForecastAiService(anthropicClient, objectMapper, baselineConfig, "claude-sonnet-4-6");
    }

    @Test
    void isAvailable_withClient_returnsTrue() {
        assertThat(aiService.isAvailable()).isTrue();
    }

    @Test
    void isAvailable_withoutClient_returnsFalse() {
        ForecastAiService noClientService = new ForecastAiService(null, objectMapper, baselineConfig, "claude-sonnet-4-6");
        assertThat(noClientService.isAvailable()).isFalse();
    }

    @Test
    void generateForecast_clientUnavailable_returnsStatisticalFallback() {
        ForecastAiService noClient = new ForecastAiService(null, objectMapper, baselineConfig, "claude-sonnet-4-6");

        ForecastContext context = buildMinimalContext();
        ForecastResult result = noClient.generateForecast(context);

        assertThat(result).isNotNull();
        assertThat(result.modelVersion()).isEqualTo("statistical-fallback");
        assertThat(result.confidenceScore()).isLessThan(new BigDecimal("60"));
        assertThat(result.netNewDeals()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void generateForecast_withSimilarIncentives_fallbackUsesWeightedAverages() {
        ForecastAiService noClient = new ForecastAiService(null, objectMapper, baselineConfig, "claude-sonnet-4-6");

        ForecastContext context = buildContextWithSimilarIncentives();
        ForecastResult result = noClient.generateForecast(context);

        assertThat(result).isNotNull();
        assertThat(result.modelVersion()).isEqualTo("statistical-fallback");
        // Should use weighted averages, not defaults
        assertThat(result.similarIncentiveIds()).hasSize(2);
        assertThat(result.budgetUtilizationPct()).isBetween(
                new BigDecimal("0"), new BigDecimal("100"));
    }

    @Test
    void generateForecast_coldStart_returnsConservativeDefaults() {
        ForecastAiService noClient = new ForecastAiService(null, objectMapper, baselineConfig, "claude-sonnet-4-6");

        ForecastContext context = buildMinimalContext();
        ForecastResult result = noClient.generateForecast(context);

        assertThat(result.confidenceScore()).isLessThanOrEqualTo(new BigDecimal("45"));
        assertThat(result.insights()).isNotEmpty();
        assertThat(result.insights().get(0).type()).isEqualTo("warning");
    }

    @Test
    void generateForecast_defaultsPerIncentiveType() {
        ForecastAiService noClient = new ForecastAiService(null, objectMapper, baselineConfig, "claude-sonnet-4-6");

        ForecastBaselineConfig.IncentiveTypeDefaults salesDefaults = baselineConfig.getDefaultsForType("SALES");
        assertThat(salesDefaults.getUtilizationPct()).isEqualTo(new BigDecimal("55"));

        ForecastBaselineConfig.IncentiveTypeDefaults trainingDefaults = baselineConfig.getDefaultsForType("TRAINING");
        assertThat(trainingDefaults.getParticipationPct()).isEqualTo(new BigDecimal("45"));
    }

    @Test
    void generateForecast_resultFieldsWithinValidRanges() {
        ForecastAiService noClient = new ForecastAiService(null, objectMapper, baselineConfig, "claude-sonnet-4-6");

        ForecastContext context = buildContextWithSimilarIncentives();
        ForecastResult result = noClient.generateForecast(context);

        assertThat(result.budgetUtilizationPct()).isBetween(
                BigDecimal.ZERO, new BigDecimal("100"));
        assertThat(result.participationRate()).isBetween(
                BigDecimal.ZERO, new BigDecimal("100"));
        assertThat(result.confidenceScore()).isBetween(
                BigDecimal.ZERO, new BigDecimal("95"));
        assertThat(result.netNewDeals()).isGreaterThanOrEqualTo(0);
        assertThat(result.estimatedParticipation()).isGreaterThanOrEqualTo(0);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private ForecastContext buildMinimalContext() {
        return new ForecastContext(
                new ForecastContext.NewIncentiveSummary(
                        "SALES", "Test Incentive", List.of("AMERICAS"), Map.of(), List.of(),
                        new ForecastContext.BudgetSummary(new BigDecimal("100000"), "cash", "GLOBAL", null),
                        new ForecastContext.DurationSummary("2026-04-01", "2026-06-30", 91),
                        null,
                        new ForecastContext.AudienceSummary(50, 200),
                        null, null, List.of("cash"),
                        null, null, false),
                List.of(), // no similar incentives
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                null,
                0, 0
        );
    }

    private ForecastContext buildContextWithSimilarIncentives() {
        return new ForecastContext(
                new ForecastContext.NewIncentiveSummary(
                        "SALES", "Test Incentive", List.of("AMERICAS"), Map.of(), List.of("Cloud Services"),
                        new ForecastContext.BudgetSummary(new BigDecimal("100000"), "cash", "GLOBAL", null),
                        new ForecastContext.DurationSummary("2026-04-01", "2026-06-30", 91),
                        null,
                        new ForecastContext.AudienceSummary(50, 200),
                        null, null, List.of("cash"),
                        new BigDecimal("3.5"), "Q2 2026", false),
                List.of(
                        new ForecastContext.SimilarIncentive(
                                "id-1", "Past Program 1", 0.8, "SALES",
                                new BigDecimal("90000"), 90,
                                new BigDecimal("72.5"), 35, new BigDecimal("40.0"),
                                new BigDecimal("350000"), new BigDecimal("65250"),
                                new BigDecimal("436.0"),
                                new BigDecimal("12.5"), new BigDecimal("45.0"), 22, new BigDecimal("60.0"),
                                List.of("Cloud Services"), List.of("AMERICAS"), "2025-12-31"),
                        new ForecastContext.SimilarIncentive(
                                "id-2", "Past Program 2", 0.65, "SALES",
                                new BigDecimal("120000"), 60,
                                new BigDecimal("85.0"), 42, new BigDecimal("48.0"),
                                new BigDecimal("500000"), new BigDecimal("102000"),
                                new BigDecimal("390.0"),
                                new BigDecimal("8.0"), new BigDecimal("52.0"), 18, new BigDecimal("70.0"),
                                List.of("Cloud Services", "Security"), List.of("AMERICAS", "EMEAR"), "2025-09-30")
                ),
                Map.of("AMERICAS", new ForecastContext.RegionSalesBaseline(
                        "00000000-0000-0000-0000-000000000001", "Region", 0, null, null,
                        145, new BigDecimal("2850000"), new BigDecimal("19655"))),
                Map.of("Cloud Services", new ForecastContext.ProductCategoryBaseline(
                        67, new BigDecimal("1340000"))),
                Map.of(),
                Map.of("AMERICAS", new ForecastContext.RegionDistribution(
                        "00000000-0000-0000-0000-000000000001", "Region", 0, null, null,
                        48, new BigDecimal("34200000"), new BigDecimal("0.58"))),
                Map.of(),
                Map.of(),
                null,
                5, 10000
        );
    }
}
