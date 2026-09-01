// Adapted from: src/pages/DashboardPage.tsx (production analog from Mirror)
import { useState, useEffect } from "react";
import { toast } from "sonner";
import { Download } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
import { useRedemptionAnalytics } from "@/hooks/useRedemptionAnalytics";
import { useAnalyticsExport } from "@/hooks/useAnalyticsExport";
import { usePermissions } from "@/hooks/usePermissions";
import { useFeatures } from "@/hooks/useFeatures";
import { RedemptionRateCard } from "@/components/redemption-analytics/RedemptionRateCard";
import { UnredeemedBalanceCard } from "@/components/redemption-analytics/UnredeemedBalanceCard";
import { FailedCancelledRateCard } from "@/components/redemption-analytics/FailedCancelledRateCard";
import { TotalCountCard } from "@/components/redemption-analytics/TotalCountCard";
import { DateRangeFilter } from "@/components/redemption-analytics/DateRangeFilter";
import { ExportConfirmDialog } from "@/components/redemption-analytics/ExportConfirmDialog";
import { AdvancedAnalyticsTab } from "@/components/analytics/advanced/AdvancedAnalyticsTab";
import { useSearchParams } from "react-router-dom";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { getCurrency, currencyIds } from "@/config/currencies";
import type { DateRange } from "@/types/redemption-analytics.types";

function formatLocalDate(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

function defaultDateFrom(): string {
  const d = new Date();
  d.setDate(d.getDate() - 30);
  return formatLocalDate(d);
}

function defaultDateTo(): string {
  return formatLocalDate(new Date());
}

function CardSkeleton() {
  return (
    <div
      role="status"
      aria-busy="true"
      aria-label="Loading analytics card"
      className="rounded-xl border border-border p-4 space-y-3"
    >
      <Skeleton className="h-4 w-32" />
      <Skeleton className="h-8 w-20" />
      <Skeleton className="h-3 w-48" />
    </div>
  );
}

export default function RedemptionAnalyticsPage() {
  const [dateRange, setDateRange] = useState<DateRange>(() => ({
    from: defaultDateFrom(),
    to: defaultDateTo(),
  }));
  const [dialogOpen, setDialogOpen] = useState(false);
  const [countdown, setCountdown] = useState(0);

  // AC-1: Advanced tab visible only when user has the permission AND the feature flag is enabled
  const { can } = usePermissions();
  const { has: hasFeature } = useFeatures();
  const showAdvancedTab =
    can("action.redemption.analytics.advanced") &&
    hasFeature("redemption_analytics_advanced");

  const { data, isLoading, isError, refetch } = useRedemptionAnalytics(
    dateRange.from,
    dateRange.to,
  );

  const { exportCsv, isPending, retryAfter, isServerError } = useAnalyticsExport();

  // Analytics load error
  useEffect(() => {
    if (isError) {
      toast.error("Could not load analytics. Please refresh.");
    }
  }, [isError]);

  // 5xx export error
  useEffect(() => {
    if (isServerError) {
      toast.error("Export failed. Please try again.");
    }
  }, [isServerError]);

  // Countdown timer when rate-limited
  useEffect(() => {
    if (retryAfter === null || retryAfter <= 0) return;
    setCountdown(retryAfter);
    const interval = setInterval(() => {
      setCountdown((prev) => {
        if (prev <= 1) {
          clearInterval(interval);
          return 0;
        }
        return prev - 1;
      });
    }, 1000);
    return () => clearInterval(interval);
  }, [retryAfter]);

  const [searchParams, setSearchParams] = useSearchParams();

  // CR-02: collapse the per-currency grid into one currency-selectable section.
  // Dropdown options = currencies present across ANY of the three metric arrays
  // (union), ordered canonically (config order first, then any custom).
  const availableCurrencies = (() => {
    if (!data) return [] as string[];
    const present = new Set<string>();
    data.redemptionRates.forEach((d) => present.add(d.currencyId));
    data.unredeemedBalances.forEach((d) => present.add(d.currencyId));
    data.failedCancelledRates.forEach((d) => present.add(d.currencyId));
    const canonical = currencyIds.filter((id) => present.has(id));
    const extras = [...present].filter((id) => !currencyIds.includes(id));
    return [...canonical, ...extras];
  })();

  // Default selection: first active currency in priority order, else first available.
  const defaultCurrency = (() => {
    if (availableCurrencies.length === 0) return undefined;
    const active = new Set(
      (data?.redemptionRates ?? [])
        .filter((r) => r.hasActivity)
        .map((r) => r.currencyId),
    );
    const PRIORITY = ["points", "cash", "credits", "tickets"];
    for (const c of PRIORITY) {
      if (availableCurrencies.includes(c) && active.has(c)) return c;
    }
    return availableCurrencies.find((c) => active.has(c)) ?? availableCurrencies[0];
  })();

  // URL-persisted selection (?currency=), falling back to the default.
  const requestedCurrency = searchParams.get("currency");
  const selectedCurrency =
    requestedCurrency && availableCurrencies.includes(requestedCurrency)
      ? requestedCurrency
      : defaultCurrency;

  function handleCurrencyChange(next: string) {
    const sp = new URLSearchParams(searchParams);
    sp.set("currency", next);
    setSearchParams(sp, { replace: true });
  }

  const selectedRate = data?.redemptionRates.find((d) => d.currencyId === selectedCurrency);
  const selectedBalance = data?.unredeemedBalances.find((d) => d.currencyId === selectedCurrency);
  const selectedFailed = data?.failedCancelledRates.find((d) => d.currencyId === selectedCurrency);

  const noActivity = !isLoading && availableCurrencies.length === 0;
  const exportDisabled = isPending || countdown > 0;
  const exportLabel =
    countdown > 0
      ? `Export limit reached. You can export again in ${countdown} seconds.`
      : "Export all balances";

  return (
    <div className="space-y-6 animate-route-in">
      <div>
        <h2 className="text-3xl font-bold tracking-tight">Redemption Analytics</h2>
        <p className="text-muted-foreground">
          Program health metrics for your organization
        </p>
      </div>

      {/* Tab bar — Overview is always active by default (AC-2).
          Advanced tab rendered only when permission + flag both true (AC-1). */}
      <Tabs defaultValue="overview">
        <TabsList>
          <TabsTrigger value="overview">Overview</TabsTrigger>
          {showAdvancedTab && (
            <TabsTrigger value="advanced">Advanced</TabsTrigger>
          )}
        </TabsList>

        {/* Overview tab — existing F-07 content */}
        <TabsContent value="overview" className="space-y-6 pt-4">
          {/* Date Filter Bar */}
          <div className="flex flex-wrap items-center justify-between gap-4">
            <DateRangeFilter value={dateRange} onChange={setDateRange} />

            {/* Export button */}
            <Button
              variant="outline"
              disabled={exportDisabled}
              aria-busy={isPending}
              aria-label={
                isPending
                  ? "Exporting…"
                  : countdown > 0
                  ? exportLabel
                  : undefined
              }
              title="Exports unredeemed balances across all currencies"
              onClick={exportDisabled ? undefined : () => setDialogOpen(true)}
            >
              <Download className="mr-2 h-4 w-4" aria-hidden />
              {exportLabel}
            </Button>
          </div>

          {/* Error retry */}
          {isError && (
            <div
              role="alert"
              aria-live="assertive"
              className="flex items-center gap-3 rounded-xl border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive"
            >
              <span>Could not load analytics data.</span>
              <Button variant="outline" size="sm" onClick={() => refetch()}>
                Try again
              </Button>
            </div>
          )}

          {/* No-activity empty state */}
          {noActivity && (
            <div
              role="status"
              className="rounded-xl border border-border bg-muted/30 px-6 py-10 text-center text-muted-foreground"
            >
              <p className="text-base font-medium">No program activity yet</p>
              <p className="text-sm mt-1">
                Metrics will appear once your organization has active reward wallets.
              </p>
            </div>
          )}

          {/* Metric section — one currency at a time (CR-02) */}
          {!noActivity && (
            <>
              {/* Currency selector */}
              {!isLoading && selectedCurrency && (
                <div className="flex items-center gap-2">
                  <Label
                    htmlFor="analytics-currency"
                    className="text-sm text-muted-foreground"
                  >
                    Currency
                  </Label>
                  <Select value={selectedCurrency} onValueChange={handleCurrencyChange}>
                    <SelectTrigger
                      id="analytics-currency"
                      className="w-[160px]"
                      aria-label="Select currency"
                    >
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {availableCurrencies.map((id) => (
                        <SelectItem key={id} value={id}>
                          {getCurrency(id.toLowerCase()).label}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              )}

              {/* Selected-currency metrics: Redemption Rate / Outstanding Liability / Failed & Cancelled */}
              <section aria-label="Currency metrics">
                <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                  {isLoading ? (
                    <>
                      <CardSkeleton />
                      <CardSkeleton />
                      <CardSkeleton />
                    </>
                  ) : (
                    <>
                      {selectedRate && <RedemptionRateCard data={selectedRate} />}
                      {selectedBalance && <UnredeemedBalanceCard data={selectedBalance} />}
                      {selectedFailed && <FailedCancelledRateCard data={selectedFailed} />}
                    </>
                  )}
                </div>
              </section>

              {/* Total Count card — global / currency-agnostic */}
              <section aria-label="Total Redemptions">
                <h3 className="text-sm font-medium text-muted-foreground mb-3">Total Redemptions</h3>
                <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
                  {isLoading ? (
                    <CardSkeleton />
                  ) : data?.totalRedemptionCount ? (
                    <TotalCountCard data={data.totalRedemptionCount} />
                  ) : null}
                </div>
              </section>
            </>
          )}
        </TabsContent>

        {/* Advanced tab — gated by permission + flag at render time (AC-1) */}
        {showAdvancedTab && (
          <TabsContent value="advanced" className="pt-4">
            <AdvancedAnalyticsTab />
          </TabsContent>
        )}
      </Tabs>

      {/* Export confirmation dialog */}
      <ExportConfirmDialog
        open={dialogOpen}
        onClose={() => setDialogOpen(false)}
        isPending={isPending}
        onConfirm={() => {
          exportCsv();
          setDialogOpen(false);
        }}
      />
    </div>
  );
}
