// Adapted from: src/components/home/widgets/ProgramPerformanceWidget.tsx (recharts LineChart production analog)
// shape: contracts/models/redemption-advanced-analytics.md → RedemptionTrendResponse
// Covers: AC-4 (story US-04)
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
import { getCurrency } from "@/config/currencies";
import type { TrendDataPointDto } from "@/types/redemption-analytics-advanced.types";

// ─── Types ───────────────────────────────────────────────────────────────────

export interface RedemptionTrendChartProps {
  /** Raw data points from the BE, ordered by periodDate ASC per spec AC-1 */
  dataPoints: TrendDataPointDto[] | undefined;
  lastRefreshedAt: string | undefined;
  isLoading: boolean;
  isError: boolean;
  refetch: () => void;
}

// ─── Constants (module-level) ─────────────────────────────────────────────────

/** Month abbreviations hoisted to module scope to avoid re-creation on every caption render */
const MONTH_NAMES = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

/**
 * Fixed line colors for the 4 known currency IDs.
 * Uses CSS custom property references so they inherit design-token dark-mode values.
 * Unknown currencies fall back to the last entry in rotation.
 */
const CURRENCY_COLORS: Record<string, string> = {
  cash: "hsl(var(--success))",
  points: "hsl(var(--primary))",
  credits: "hsl(259 60% 58%)",  // violet — no semantic token for credits
  tickets: "hsl(var(--warning))",
};

const FALLBACK_COLORS = [
  "hsl(175 60% 42%)",
  "hsl(340 65% 55%)",
  "hsl(30 80% 52%)",
];

/** Hoisted module-level formatter — avoids a new function reference on every render */
const formatYAxisTick = (v: number) => `${v}%`;

/** Chart height in px — drives both the real chart and the loading skeleton */
const CHART_HEIGHT = 280;

// ─── Helpers ─────────────────────────────────────────────────────────────────

/**
 * Format the "Data as of {date} at {time} UTC" caption.
 * Uses UTC representation to avoid timezone-skew bugs (anti-pattern: Date.toISOString()
 * converting to UTC shifts users east of UTC to the previous calendar day).
 */
function formatCaption(lastRefreshedAt: string): string {
  const iso = new Date(lastRefreshedAt).toISOString(); // e.g. "2026-06-20T06:00:00.000Z"
  const datePart = iso.slice(0, 10);                   // "2026-06-20"
  const timePart = iso.slice(11, 16);                  // "06:00"
  const [year, month, day] = datePart.split("-").map(Number) as [number, number, number];
  const formattedDate = `${MONTH_NAMES[month - 1]} ${day}, ${year}`;
  return `Data as of ${formattedDate} at ${timePart} UTC`;
}

/**
 * Format a periodDate string ("YYYY-MM-DD") as "MMM d" for the X-axis tick.
 * Parses the ISO date string directly without constructing a Date object to
 * avoid local-timezone day-boundary issues.
 */
function formatAxisDate(periodDate: string): string {
  const [, month, day] = periodDate.split("-").map(Number) as [number, number, number];
  return `${MONTH_NAMES[month - 1]} ${day}`;
}

/**
 * Get the line stroke color for a given currencyId.
 * Normalises BE-emitted uppercase IDs (e.g. "POINTS") to lowercase.
 */
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
 * Input:  [{ periodDate, currencyId, redemptionRate, redeemedCount }, ...]
 * Output: { chartData: [{ date, POINTS: 0.15, CASH: 0.10, ... }],
 *            currencies: ["POINTS", "CASH", ...] }
 *
 * The pivot key uses the original (upper-case) currencyId as the recharts
 * dataKey so Line components can reference them directly.
 */
function pivotDataPoints(dataPoints: TrendDataPointDto[]): {
  chartData: Record<string, string | number>[];
  currencies: string[];
} {
  // Collect distinct currencies in order of first appearance (already ordered ASC from BE)
  const currenciesSet = new Set<string>();
  const byDate = new Map<string, Record<string, string | number>>();

  for (const dp of dataPoints) {
    currenciesSet.add(dp.currencyId);
    let row = byDate.get(dp.periodDate);
    if (!row) {
      row = { date: dp.periodDate };
      byDate.set(dp.periodDate, row);
    }
    // Store the rate as a number (0–100); tooltip also needs redeemedCount per currency
    row[dp.currencyId] = dp.redemptionRate;
    row[`${dp.currencyId}_count`] = dp.redeemedCount;
  }

  return {
    chartData: Array.from(byDate.values()),
    currencies: Array.from(currenciesSet),
  };
}

// ─── Custom Tooltip ───────────────────────────────────────────────────────────

interface TrendTooltipPayloadItem {
  dataKey: string;
  value: number;
  payload: Record<string, string | number>;
  color: string;
}

function TrendTooltip({ active, payload, label }: TooltipProps<number, string> & {
  payload?: TrendTooltipPayloadItem[];
}) {
  if (!active || !payload?.length) return null;

  return (
    <div className="rounded-lg border bg-popover px-3 py-2 shadow-md text-sm space-y-1">
      <p className="font-medium text-foreground">{typeof label === "string" ? formatAxisDate(label) : label}</p>
      {payload.map((entry) => {
        // Skip the _count keys — we use them for the label but don't render them as rows
        if (String(entry.dataKey).endsWith("_count")) return null;
        const currencyId = String(entry.dataKey);
        const currencyLabel = getCurrency(currencyId.toLowerCase()).label;
        const count = entry.payload[`${currencyId}_count`];
        return (
          <div key={currencyId} className="space-y-0.5">
            <p className="text-xs font-medium" style={{ color: entry.color }}>
              {currencyLabel}
            </p>
            <p className="text-xs text-muted-foreground">
              Redeemed: {typeof count === "number" ? count.toLocaleString() : count}
            </p>
            <p className="text-xs text-muted-foreground">
              Rate: {typeof entry.value === "number" ? entry.value.toFixed(2) : entry.value}%
            </p>
          </div>
        );
      })}
    </div>
  );
}

// ─── Component ────────────────────────────────────────────────────────────────

/**
 * RedemptionTrendChart — renders the FR-08.4 redemption rate trend.
 *
 * A recharts LineChart with one <Line> per distinct currencyId, X-axis = periodDate,
 * Y-axis = redemption rate %. Custom tooltip shows redeemedCount and redemptionRate.
 *
 * States: loading (rectangle skeleton), empty ("No data for the selected period"),
 *         error ("Unable to load redemption rate trend" + Retry button), data.
 */
export function RedemptionTrendChart({
  dataPoints,
  lastRefreshedAt,
  isLoading,
  isError,
  refetch,
}: RedemptionTrendChartProps) {
  // ── Error state ────────────────────────────────────────────────────────────
  if (isError) {
    return (
      <div
        role="alert"
        aria-live="assertive"
        className="flex flex-col items-center gap-3 rounded-lg border border-destructive/20 bg-destructive/5 px-6 py-8 text-center"
      >
        <p className="text-sm text-destructive">Unable to load redemption rate trend</p>
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
        aria-label="Loading Redemption Rate Trend"
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
      <div role="status" className="flex flex-col items-center gap-2 py-10 text-center">
        <p className="text-sm text-muted-foreground">No data for the selected period</p>
      </div>
    );
  }

  // ── Data chart ─────────────────────────────────────────────────────────────
  const { chartData, currencies } = pivotDataPoints(dataPoints);

  return (
    <div className="space-y-2">
      <div className="rounded-lg border p-4">
        <ResponsiveContainer width="100%" height={CHART_HEIGHT}>
          <LineChart
            data={chartData}
            margin={{ top: 8, right: 16, bottom: 8, left: 8 }}
            aria-label="Redemption Rate Trend chart"
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
                value: "Rate (%)",
                angle: -90,
                position: "insideLeft",
                offset: 8,
                style: { fill: "hsl(var(--muted-foreground))", fontSize: 11 },
              }}
              width={56}
            />
            <Tooltip content={<TrendTooltip />} />
            {currencies.map((currencyId) => (
              <Line
                key={currencyId}
                type="monotone"
                dataKey={currencyId}
                name={getCurrency(currencyId.toLowerCase()).label}
                stroke={getLineColor(currencyId)}
                strokeWidth={2}
                dot={{ fill: "hsl(var(--card))", stroke: getLineColor(currencyId), strokeWidth: 2, r: 3 }}
                activeDot={{ r: 5 }}
                connectNulls
              />
            ))}
          </LineChart>
        </ResponsiveContainer>
      </div>

      {/* "Data as of …" caption (AC-4, FR-08.8) */}
      {lastRefreshedAt && (
        <p className="text-xs text-muted-foreground">
          <time dateTime={lastRefreshedAt}>{formatCaption(lastRefreshedAt)}</time>
        </p>
      )}
    </div>
  );
}
