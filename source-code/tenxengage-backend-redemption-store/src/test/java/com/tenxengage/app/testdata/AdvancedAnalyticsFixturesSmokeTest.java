package com.tenxengage.app.testdata;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdvancedAnalyticsFixturesSmokeTest {

    @Mock private JdbcTemplate jdbc;
    @InjectMocks private AdvancedAnalyticsFixtures fixtures;

    private static final UUID CLIENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ITEM_ID   = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final LocalDate DATE = LocalDate.of(2026, 6, 22);

    @Test
    void insertItemBreakdownRow_runsWithoutError() {
        assertThatNoException().isThrownBy(() ->
                fixtures.insertItemBreakdownRow(
                        CLIENT_ID, ITEM_ID, "Gift Card", "cash",
                        "APAC", "INSTANT", DATE,
                        100, BigDecimal.valueOf(1000), BigDecimal.valueOf(85.5),
                        5, 3));
    }

    @Test
    void insertSegmentBreakdownRow_runsWithoutError() {
        assertThatNoException().isThrownBy(() ->
                fixtures.insertSegmentBreakdownRow(
                        CLIENT_ID, "APAC", "CLIENT_ADMIN", "cash",
                        DATE, 50, BigDecimal.valueOf(500), BigDecimal.valueOf(90.0)));
    }

    @Test
    void insertTimeToFirstRedemptionRow_runsWithoutError() {
        assertThatNoException().isThrownBy(() ->
                fixtures.insertTimeToFirstRedemptionRow(
                        CLIENT_ID, "APAC", DATE, 24.5, 20.0, 245.0, 10));
    }

    @Test
    void insertRedemptionTrendRow_runsWithoutError() {
        assertThatNoException().isThrownBy(() ->
                fixtures.insertRedemptionTrendRow(
                        CLIENT_ID, DATE, "cash", 200, BigDecimal.valueOf(78.5)));
    }

    @Test
    void insertLiabilityTrendRow_returnsIdFromJdbc() {
        UUID expected = UUID.randomUUID();
        // queryForObject(sql, UUID.class, clientId, periodDate, currencyType, balance) — 4 vararg args
        when(jdbc.queryForObject(anyString(), eq(UUID.class), any(), any(), any(), any()))
                .thenReturn(expected);

        UUID actual = fixtures.insertLiabilityTrendRow(
                CLIENT_ID, DATE, "cash", BigDecimal.valueOf(5000));

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void insertFailureBreakdownRow_runsWithoutError() {
        assertThatNoException().isThrownBy(() ->
                fixtures.insertFailureBreakdownRow(
                        CLIENT_ID, "INSTANT", ITEM_ID, "Gift Card", "cash",
                        "APAC", DATE, 10, 5, 15, BigDecimal.valueOf(100)));
    }

    @Test
    void upsertRefreshLog_returnsIdFromJdbc() {
        UUID expected = UUID.randomUUID();
        // queryForObject(sql, UUID.class, mvName, lastRefreshedAt, durationMs) — 3 vararg args
        when(jdbc.queryForObject(anyString(), eq(UUID.class), any(), any(), any()))
                .thenReturn(expected);

        UUID actual = fixtures.upsertRefreshLog(
                "mv_item_redemption_breakdown", Instant.parse("2026-06-22T14:00:00Z"), 1500L);

        assertThat(actual).isEqualTo(expected);
    }
}
