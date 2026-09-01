package com.tenxengage.app.service.redemption;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsMvRefreshSchedulerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private AnalyticsMvRefreshScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new AnalyticsMvRefreshScheduler(jdbcTemplate);
    }

    @Test
    void refreshAllMvs_happyPath_refreshesAllFiveMvsAndUpsertRefreshLog() {
        scheduler.refreshAllMvs();

        // All 5 REFRESH MATERIALIZED VIEW commands issued
        ArgumentCaptor<String> executeCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(5)).execute(executeCaptor.capture());
        List<String> executedSqls = executeCaptor.getAllValues();
        for (String mvName : AnalyticsMvRefreshScheduler.MV_NAMES) {
            assertThat(executedSqls).anyMatch(sql -> sql.endsWith(mvName));
        }
        // Regression guard: must NOT use CONCURRENTLY — the MVs' unique indexes are
        // expression indexes (COALESCE(region,'')), which CONCURRENTLY rejects, so the
        // refresh would throw and be silently swallowed. See AnalyticsMvRefreshScheduler.refreshMv.
        assertThat(executedSqls).noneMatch(sql -> sql.contains("CONCURRENTLY"));

        // Refresh log upserted once per MV: update(sql, mvName:String, durationMs:Long)
        verify(jdbcTemplate, times(5)).update(contains("analytics_mv_refresh_log"),
                any(String.class), any(Long.class));

        // Liability snapshot issued as a single-arg update (no Java params — SQL is self-contained)
        verify(jdbcTemplate).update(contains("mv_liability_trend"));
    }

    @Test
    void refreshAllMvs_partialFailure_failedMvSkipsLogUpsert_otherMvsStillRefreshed() {
        doAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.endsWith("mv_item_redemption_breakdown")) {
                throw new DataRetrievalFailureException("simulated refresh failure");
            }
            return null;
        }).when(jdbcTemplate).execute(anyString());

        scheduler.refreshAllMvs();

        // All 5 MVs were still attempted despite the failure
        verify(jdbcTemplate, times(5)).execute(anyString());

        // Only 4 successful log upserts (failed MV skips — no refresh_status column)
        ArgumentCaptor<String> mvNameCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(4)).update(contains("analytics_mv_refresh_log"),
                mvNameCaptor.capture(), any(Long.class));
        assertThat(mvNameCaptor.getAllValues()).doesNotContain("mv_item_redemption_breakdown");

        // Liability snapshot still issued
        verify(jdbcTemplate).update(contains("mv_liability_trend"));
    }

    @Test
    void insertLiabilitySnapshot_issuesInsertOnConflictAgainstRewardWallets() {
        scheduler.insertLiabilitySnapshot();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sqlCaptor.capture());
        String sql = sqlCaptor.getValue();
        assertThat(sql).contains("mv_liability_trend");
        assertThat(sql).contains("reward_wallets");
        assertThat(sql).containsIgnoringCase("ON CONFLICT");
        assertThat(sql).containsIgnoringCase("DO UPDATE");
    }
}
