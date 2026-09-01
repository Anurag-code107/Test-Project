// Adapted from: src/pages/redemption/analytics/RedemptionAnalyticsPage.tsx (dashboard pattern)
// No mockup — no production analog for this advanced analytics tab container.
import { useState } from "react";
import { AdvancedFilterBar } from "@/components/analytics/advanced/AdvancedFilterBar";
import { StalenessBanner } from "@/components/analytics/advanced/StalenessBanner";
import { ItemBreakdownTable } from "@/components/analytics/advanced/ItemBreakdownTable";
import { SegmentBreakdownTable } from "@/components/analytics/advanced/SegmentBreakdownTable";
import { TimeToFirstRedemptionTable } from "@/components/analytics/advanced/TimeToFirstRedemptionTable";
import { RedemptionTrendChart } from "@/components/analytics/advanced/RedemptionTrendChart";
import { LiabilityTrendChart } from "@/components/analytics/advanced/LiabilityTrendChart";
import { FailureBreakdownTable } from "@/components/analytics/advanced/FailureBreakdownTable";
import { useRefreshStatus } from "@/hooks/redemption/useRefreshStatus";
import { useSegmentBreakdown } from "@/hooks/redemption/useSegmentBreakdown";
import { useItemBreakdown } from "@/hooks/redemption/useItemBreakdown";
import { useTimeToFirstRedemption } from "@/hooks/redemption/useTimeToFirstRedemption";
import { useRedemptionTrend } from "@/hooks/redemption/useRedemptionTrend";
import { useLiabilityTrend } from "@/hooks/redemption/useLiabilityTrend";
import { useFailureBreakdown } from "@/hooks/redemption/useFailureBreakdown";
import type { AdvancedAnalyticsFilters } from "@/types/redemption-analytics-advanced.types";

function formatLocalDate(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

function defaultFilters(): AdvancedAnalyticsFilters {
  const today = new Date();
  const from = new Date();
  from.setDate(from.getDate() - 30);
  return {
    dateFrom: formatLocalDate(from),
    dateTo: formatLocalDate(today),
  };
}

/**
 * AdvancedAnalyticsTab — composes the filter bar, staleness banner, and section
 * content for US-01–US-07. Sections are replaced by real components as each US lands.
 *
 * Permission + flag guard is enforced at the parent page level
 * (RedemptionAnalyticsPage), so this component renders only when both
 * action.redemption.analytics.advanced AND redemption_analytics_advanced are true.
 */
export function AdvancedAnalyticsTab() {
  const [filters, setFilters] = useState<AdvancedAnalyticsFilters>(defaultFilters);

  // Poll /refresh-status every 5 minutes (AC-7); silent fail on error (negative path)
  const { data: refreshStatus } = useRefreshStatus();

  // Segment breakdown (US-02) — data used for both filter bar empty-state gate and the table
  const {
    data: segmentBreakdownData,
    isLoading: segmentBreakdownLoading,
    isError: segmentBreakdownError,
    refetch: segmentBreakdownRefetch,
  } = useSegmentBreakdown(filters);
  const isSegmentDataEmpty = (segmentBreakdownData?.segments?.length ?? 0) === 0;

  // Distinct, non-null region/role values from the segment breakdown response drive the
  // filter-bar multi-select options (FR-08.6 — "options sourced from distinct values").
  const regionOptions = Array.from(
    new Set(
      (segmentBreakdownData?.segments ?? [])
        .map((s) => s.region)
        .filter((r): r is string => !!r),
    ),
  ).sort();
  const roleOptions = Array.from(
    new Set(
      (segmentBreakdownData?.segments ?? [])
        .map((s) => s.role)
        .filter((r): r is string => !!r),
    ),
  ).sort();

  // Item breakdown (US-01)
  const {
    data: itemBreakdownData,
    isLoading: itemBreakdownLoading,
    isError: itemBreakdownError,
    refetch: itemBreakdownRefetch,
  } = useItemBreakdown(filters);

  // Time to first redemption (US-03)
  const {
    data: ttfrData,
    isLoading: ttfrLoading,
    isError: ttfrError,
    refetch: ttfrRefetch,
  } = useTimeToFirstRedemption(filters);

  // Redemption trend (US-04) — tenant-wide, no region/role filter per spec FR-08.4
  const {
    data: trendData,
    isLoading: trendLoading,
    isError: trendError,
    refetch: trendRefetch,
  } = useRedemptionTrend(filters.dateFrom, filters.dateTo);

  // Liability trend (US-06) — tenant-wide, no region/role filter per spec FR-08.5
  const {
    data: liabilityTrendData,
    isLoading: liabilityTrendLoading,
    isError: liabilityTrendError,
    refetch: liabilityTrendRefetch,
  } = useLiabilityTrend(filters.dateFrom, filters.dateTo);

  // Failure breakdown (US-07) — filterable by region + date range per spec FR-08.7
  const {
    data: failureBreakdownData,
    isLoading: failureBreakdownLoading,
    isError: failureBreakdownError,
    refetch: failureBreakdownRefetch,
  } = useFailureBreakdown(filters);

  const isStale = refreshStatus?.isStale ?? false;
  // StalenessBanner expects string | null; coerce undefined → null
  const lastRefreshedAt = refreshStatus?.lastRefreshedAt ?? null;

  return (
    <div className="space-y-6">
      {/* Staleness banner (AC-6, AC-7) */}
      <StalenessBanner isStale={isStale} lastRefreshedAt={lastRefreshedAt} />

      {/* Filter bar (AC-3, AC-4) */}
      <AdvancedFilterBar
        onFilterChange={setFilters}
        isSegmentDataEmpty={isSegmentDataEmpty}
        currentFilters={filters}
        regionOptions={regionOptions}
        roleOptions={roleOptions}
      />

      {/* Sections */}
      <div className="space-y-8">
        {/* US-01: Item Breakdown */}
        <section aria-label="Item Breakdown" className="space-y-3">
          <h3 className="text-sm font-semibold text-foreground">Item Breakdown</h3>
          <ItemBreakdownTable
            items={itemBreakdownData?.items}
            lastRefreshedAt={itemBreakdownData?.lastRefreshedAt}
            isLoading={itemBreakdownLoading}
            isError={itemBreakdownError}
            refetch={itemBreakdownRefetch}
          />
        </section>
        {/* US-02: Segment Breakdown */}
        <section aria-label="Segment Breakdown" className="space-y-3">
          <h3 className="text-sm font-semibold text-foreground">Segment Breakdown</h3>
          <SegmentBreakdownTable
            segments={segmentBreakdownData?.segments}
            lastRefreshedAt={segmentBreakdownData?.lastRefreshedAt}
            isLoading={segmentBreakdownLoading}
            isError={segmentBreakdownError}
            refetch={segmentBreakdownRefetch}
          />
        </section>
        {/* US-03: Time to First Redemption */}
        <section aria-label="Time to First Redemption" className="space-y-3">
          <h3 className="text-sm font-semibold text-foreground">Time to First Redemption</h3>
          <TimeToFirstRedemptionTable
            regions={ttfrData?.regions}
            lastRefreshedAt={ttfrData?.lastRefreshedAt}
            isLoading={ttfrLoading}
            isError={ttfrError}
            refetch={ttfrRefetch}
          />
        </section>
        {/* US-04: Redemption Rate Trend */}
        <section aria-label="Redemption Rate Trend" className="space-y-3">
          <h3 className="text-sm font-semibold text-foreground">Redemption Rate Trend</h3>
          <RedemptionTrendChart
            dataPoints={trendData?.dataPoints}
            lastRefreshedAt={trendData?.lastRefreshedAt}
            isLoading={trendLoading}
            isError={trendError}
            refetch={trendRefetch}
          />
        </section>
        {/* US-06: Liability Trend */}
        <section aria-label="Liability Trend" className="space-y-3">
          <h3 className="text-sm font-semibold text-foreground">Liability Trend</h3>
          <LiabilityTrendChart
            dataPoints={liabilityTrendData?.dataPoints}
            lastRefreshedAt={liabilityTrendData?.lastRefreshedAt}
            isLoading={liabilityTrendLoading}
            isError={liabilityTrendError}
            refetch={liabilityTrendRefetch}
            dateFrom={filters.dateFrom}
            dateTo={filters.dateTo}
          />
        </section>
        {/* US-07: Failure Breakdown */}
        <section aria-label="Failure Breakdown" className="space-y-3">
          <h3 className="text-sm font-semibold text-foreground">Failure Breakdown</h3>
          <FailureBreakdownTable
            failureModes={failureBreakdownData?.failureModes}
            lastRefreshedAt={failureBreakdownData?.lastRefreshedAt}
            isLoading={failureBreakdownLoading}
            isError={failureBreakdownError}
            refetch={failureBreakdownRefetch}
          />
        </section>
      </div>
    </div>
  );
}
