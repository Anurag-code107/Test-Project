// Adapted from: src/components/analytics/advanced/RedemptionTrendChart.tsx (recharts LineChart pattern)
// shape: contracts/models/redemption-advanced-analytics.md → LiabilityTrendResponse
// Covers: AC-2, AC-4, AC-6 (story US-06)
import { useState, useEffect, useRef, useCallback } from "react";
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  type TooltipProps,
} from "recharts";
import { Skeleton } from "@/components/ui/skeleton";
import { Button } from "@/components/ui/button";
import { Loader2 } from "lucide-react";
import { toast } from "sonner";
import { getCurrency } from "@/config/currencies";
import {
  exportLiabilityTrendCsv,
  ExportRateLimitedError,
} from "@/services/redemption-analytics-advanced.service";
import type { LiabilityDataPointDto } from "@/types/redemption-analytics-advanced.types";

// ─── Types ───────────────────────────────────────────────────────────────────

export interface LiabilityTrendChartProps {
  /** Raw data points from the BE, ordered by periodDate ASC per spec AC-1 */
  dataPoints: LiabilityDataPointDto[] | undefined;
  lastRefreshedAt: string | undefined;
  isLoading: boolean;
  isError: boolean;
  refetch: () => void;
  /** Date range for the export endpoint; forwarded from the filter bar */
  dateFrom: string;
  dateTo: string;
}

// ─── Constants (module-level) ─────────────────────────────────────────────────

const MONTH_NAMES = [
  "Jan", "Feb", "Mar", "Apr", "May", "Jun",
  "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
];

const CURRENCY_COLORS: Record<string, string> = {
  cash: "hsl(var(--success))",
  points: "hsl(var(--primary))",
  credits: "hsl(259 60% 58%)",
  tickets: "hsl(var(--warning))",
};

const FALLBACK_COLORS = [
  "hsl(175 60% 42%)",
  "hsl(340 65% 55%)",
  "hsl(30 80% 52%)",
];

/** Chart height in px — drives both the real chart and the loading skeleton */
const CHART_HEIGHT = 280;

// ─── Helpers ─────────────────────────────────────────────────────────────────

function formatCaption(lastRefreshedAt: string): string {
  const iso = new Date(lastRefreshedAt).toISOString();
  const datePart = iso.slice(0, 10);
  const timePart = iso.slice(11, 16);
  const [year, month, day] = datePart.split("-").map(Number) as [number, number, number];
  const formattedDate = `${MONTH_NAMES[month - 1]} ${day}, ${year}`;
  return `Data as of ${formattedDate} at ${timePart} UTC`;
}

function formatAxisDate(periodDate: string): string {
  const [, month, day] = periodDate.split("-").map(Number) as [number, number, number];
  return `${MONTH_NAMES[month - 1]} ${day}`;
}

/** Module-level fallback color state — per the existing RedemptionTrendChart pattern */
let _fallbackIdx = 0;
const _fallbackMap: Record<string, string> = {};

function getLineColor(currencyId: string): string {
  const key = currencyId.toLowerCase();
  if (key in CURRENCY_COLORS) return CURRENCY_COLORS[key]!;
  if (key in _fallbackMap) return _fallbackMap[key]!;
  const color = FALLBACK_COLORS[_fallbackIdx % FALLBACK_COLORS.length]!;
  _fallbackMap[key] = color;
  _fallbackIdx++;
  return color;
}

/**
 * Group flat dataPoints by currencyId and pivot to recharts chart data shape.
 *
 * Input:  [{ periodDate, currencyId, totalUnredeemedBalance }, ...]
 * Output: { chartData: [{ date, POINTS: "1200.50", CASH: "500.00", ... }],
 *            currencies: ["POINTS", "CASH", ...] }
 */
function pivotDataPoints(dataPoints: LiabilityDataPointDto[]): {
  chartData: Record<string, string | number>[];
  currencies: string[];
} {
  const currenciesSet = new Set<string>();
  const byDate = new Map<string, Record<string, string | number>>();

  for (const dp of dataPoints) {
    currenciesSet.add(dp.currencyId);
    let row = byDate.get(dp.periodDate);
    if (!row) {
      row = { date: dp.periodDate };
      byDate.set(dp.periodDate, row);
    }
    // Store as number for recharts axis; totalUnredeemedBalance is a BigDecimal string from BE
    row[dp.currencyId] = parseFloat(dp.totalUnredeemedBalance);
  }

  return {
    chartData: Array.from(byDate.values()),
    currencies: Array.from(currenciesSet),
  };
}

const formatYAxisTick = (v: number) =>
  v >= 1_000 ? `${(v / 1_000).toFixed(0)}k` : String(v);

// ─── Custom Tooltip ───────────────────────────────────────────────────────────

interface LiabilityTooltipPayloadItem {
  dataKey: string;
  value: number;
  color: string;
}

function LiabilityTooltip({
  active,
  payload,
  label,
}: TooltipProps<number, string> & { payload?: LiabilityTooltipPayloadItem[] }) {
  if (!active || !payload?.length) return null;

  return (
    <div className="rounded-lg border bg-popover px-3 py-2 shadow-md text-sm space-y-1">
      <p className="font-medium text-foreground">
        {typeof label === "string" ? formatAxisDate(label) : label}
      </p>
      {payload.map((entry) => {
        const currencyId = String(entry.dataKey);
        const currencyLabel = getCurrency(currencyId.toLowerCase()).label;
        return (
          <div key={currencyId} className="space-y-0.5">
            <p className="text-xs font-medium" style={{ color: entry.color }}>
              {currencyLabel}
            </p>
            <p className="text-xs text-muted-foreground">
              Balance:{" "}
              {typeof entry.value === "number" ? entry.value.toLocaleString() : entry.value}
            </p>
          </div>
        );
      })}
    </div>
  );
}

// ─── Component ────────────────────────────────────────────────────────────────

/**
 * LiabilityTrendChart — renders the FR-08.5 unredeemed balance liability trend.
 *
 * A recharts LineChart with one <Line> per distinct currencyId, X-axis = periodDate,
 * Y-axis = "Unredeemed Balance". CSV export button with rate-limit countdown state.
 *
 * States: loading (rectangle skeleton), empty ("No data for the selected period"),
 *         error ("Unable to load liability trend" + Retry button), data.
 *
 * Export button states:
 *   - idle: "Export CSV"
 *   - in-flight: spinner + disabled
 *   - 429 rate-limited: "Retry in {N}s" + disabled; countdown via setInterval
 *   - non-429 error: button re-enables; toast "Export failed — please try again"
 */
export function LiabilityTrendChart({
  dataPoints,
  lastRefreshedAt,
  isLoading,
  isError,
  refetch,
  dateFrom,
  dateTo,
}: LiabilityTrendChartProps) {
  const [isExporting, setIsExporting] = useState(false);
  const [retryAfterSeconds, setRetryAfterSeconds] = useState<number | null>(null);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  // Clear countdown interval on unmount
  useEffect(() => {
    return () => {
      if (intervalRef.current !== null) {
        clearInterval(intervalRef.current);
      }
    };
  }, []);

  const handleExport = useCallback(async () => {
    if (isExporting || retryAfterSeconds !== null) return;

    setIsExporting(true);
    try {
      const blob = await exportLiabilityTrendCsv(dateFrom, dateTo);

      // Trigger browser download
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = "redemption-liability-trend.csv";
      anchor.click();
      URL.revokeObjectURL(url);
    } catch (err) {
      if (err instanceof ExportRateLimitedError) {
        // Start countdown; button re-enables when it reaches 0
        setRetryAfterSeconds(err.retryAfterSeconds);
        intervalRef.current = setInterval(() => {
          setRetryAfterSeconds((prev) => {
            if (prev === null || prev <= 1) {
              if (intervalRef.current !== null) {
                clearInterval(intervalRef.current);
                intervalRef.current = null;
              }
              return null;
            }
            return prev - 1;
          });
        }, 1_000);
      } else {
        // Non-429 error: re-enable the button and show a toast (AC-6)
        toast.error("Export failed — please try again");
      }
    } finally {
      setIsExporting(false);
    }
  }, [isExporting, retryAfterSeconds, dateFrom, dateTo]);

  // ── Error state ────────────────────────────────────────────────────────────
  if (isError) {
    return (
      <div
        role="alert"
        aria-live="assertive"
        className="flex flex-col items-center gap-3 rounded-lg border border-destructive/20 bg-destructive/5 px-6 py-8 text-center"
      >
        <p className="text-sm text-destructive">Unable to load liability trend</p>
        <Button variant="outline" size="sm" onClick={refetch}>
          Retry
        </Button>
      </div>
    );
  }

  // ── Loading skeleton ───────────────────────────────────────────────────────
  if (isLoading) {
    return (
      <div
        role="status"
        aria-busy="true"
        aria-label="Loading Liability Trend"
        className="rounded-lg border p-4"
      >
        <Skeleton
          className="w-full rounded"
          style={{ height: CHART_HEIGHT }}
        />
      </div>
    );
  }

  // ── Empty state ────────────────────────────────────────────────────────────
  if (!dataPoints || dataPoints.length === 0) {
    return (
      <div className="space-y-3">
        <div role="status" className="flex flex-col items-center gap-2 py-10 text-center">
          <p className="text-sm text-muted-foreground">No data for the selected period</p>
        </div>
        {/* Export button visible even in empty state so the user can try the download */}
        <div className="flex justify-end">
          <ExportButton
            isExporting={isExporting}
            retryAfterSeconds={retryAfterSeconds}
            onClick={handleExport}
          />
        </div>
      </div>
    );
  }

  // ── Data chart ─────────────────────────────────────────────────────────────
  const { chartData, currencies } = pivotDataPoints(dataPoints);

  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between gap-2">
        {/* "Data as of …" caption (AC-6, FR-08.8) */}
        {lastRefreshedAt ? (
          <p className="text-xs text-muted-foreground">
            <time dateTime={lastRefreshedAt}>{formatCaption(lastRefreshedAt)}</time>
          </p>
        ) : (
          <span />
        )}
        <ExportButton
          isExporting={isExporting}
          retryAfterSeconds={retryAfterSeconds}
          onClick={handleExport}
        />
      </div>

      <div className="rounded-lg border p-4">
        <ResponsiveContainer width="100%" height={CHART_HEIGHT}>
          <LineChart
            data={chartData}
            margin={{ top: 8, right: 16, bottom: 8, left: 8 }}
            aria-label="Liability Trend chart"
          >
            <CartesianGrid
              strokeDasharray="3 3"
              stroke="hsl(var(--border))"
              vertical={false}
            />
            <XAxis
              dataKey="date"
              tickLine={false}
              axisLine={false}
              fontSize={11}
              tick={{ fill: "hsl(var(--muted-foreground))" }}
              tickFormatter={formatAxisDate}
              dy={4}
            />
            <YAxis
              tickLine={false}
              axisLine={false}
              fontSize={11}
              tick={{ fill: "hsl(var(--muted-foreground))" }}
              tickFormatter={formatYAxisTick}
              label={{
                value: "Unredeemed Balance",
                angle: -90,
                position: "insideLeft",
                offset: 12,
                style: { fill: "hsl(var(--muted-foreground))", fontSize: 11 },
              }}
              width={72}
            />
            <Tooltip content={<LiabilityTooltip />} />
            {currencies.map((currencyId) => (
              <Line
                key={currencyId}
                type="monotone"
                dataKey={currencyId}
                name={getCurrency(currencyId.toLowerCase()).label}
                stroke={getLineColor(currencyId)}
                strokeWidth={2}
                dot={{
                  fill: "hsl(var(--card))",
                  stroke: getLineColor(currencyId),
                  strokeWidth: 2,
                  r: 3,
                }}
                activeDot={{ r: 5 }}
                connectNulls
              />
            ))}
          </LineChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}

// ─── Export button sub-component ───────────────────────────────────────────────

interface ExportButtonProps {
  isExporting: boolean;
  retryAfterSeconds: number | null;
  onClick: () => void;
}

function ExportButton({ isExporting, retryAfterSeconds, onClick }: ExportButtonProps) {
  const isRateLimited = retryAfterSeconds !== null;
  const isDisabled = isExporting || isRateLimited;

  let label: React.ReactNode;
  if (isRateLimited) {
    label = `Retry in ${retryAfterSeconds}s`;
  } else if (isExporting) {
    label = (
      <>
        <Loader2 aria-hidden="true" className="mr-1 h-3.5 w-3.5 animate-spin motion-reduce:animate-none" />
        Exporting…
      </>
    );
  } else {
    label = "Export CSV";
  }

  return (
    <Button
      variant="outline"
      size="sm"
      onClick={isDisabled ? undefined : onClick}
      disabled={isDisabled}
      aria-label={isRateLimited ? `Retry in ${retryAfterSeconds} seconds` : "Export CSV"}
    >
      {label}
    </Button>
  );
}
