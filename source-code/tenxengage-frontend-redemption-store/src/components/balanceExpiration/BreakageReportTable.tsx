// shape: contracts/models/balance-breakage-report.md (BreakageRowDto)
// Adapted from: src/components/analytics/advanced/ItemBreakdownTable.tsx (shadcn Table + getCurrency pattern)
import { useState, useCallback, useRef } from "react";
import {
  useReactTable,
  getCoreRowModel,
  flexRender,
  createColumnHelper,
} from "@tanstack/react-table";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Skeleton } from "@/components/ui/skeleton";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Label } from "@/components/ui/label";
import { Input } from "@/components/ui/input";
import { AlertCircle, Download, FileBarChart2 } from "lucide-react";
import { toast } from "sonner";
import { isAxiosError } from "axios";
import { getCurrency } from "@/config/currencies";
import { formatDate } from "@/utils/formatters";
import { useBalanceBreakage } from "@/hooks/useBalanceBreakage";
import { exportBreakage } from "@/services/balanceExpiration.service";
import type { BreakageRowDto, BreakageGranularity } from "@/types/balanceExpiration.types";

// ─── Helpers ──────────────────────────────────────────────────────────────────

/**
 * Format a YYYY-MM-DD date field from the server into a display string.
 * The `date` param is already a date-only ISO string from the API (no time component),
 * so we parse the parts directly to avoid the UTC→local conversion that
 * `new Date("2026-01-01")` performs (interprets as midnight UTC, not local date).
 * Anti-pattern avoided: never use Date.toISOString() to format date-only strings
 * (PROJECT-CONTEXT.md: produces previous calendar day for UTC+ timezones).
 */
function formatDateField(dateStr: string): string {
  if (!dateStr) return "";
  // dateStr is YYYY-MM-DD; parse into local year/month/day for display
  const parts = dateStr.split("-").map(Number);
  const year = parts[0] ?? 2000;
  const month = parts[1] ?? 1;
  const day = parts[2] ?? 1;
  return formatDate(new Date(year, month - 1, day));
}

/**
 * Build a YYYY-MM-DD string from a Date using local calendar fields.
 * Avoids toISOString() UTC-shift (PROJECT-CONTEXT.md anti-pattern).
 */
function toLocalDateString(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

// ─── Default date range: last 3 months (within 24-month cap) ─────────────────

// Lazy init to avoid re-running on every render (PROJECT-CONTEXT.md: use lazy useState for Date work)
function getDefaultDates(): { from: string; to: string } {
  const to = new Date();
  const from = new Date(to);
  from.setMonth(from.getMonth() - 3);
  return { from: toLocalDateString(from), to: toLocalDateString(to) };
}

// ─── Column definitions — hoisted to module scope (no new reference per render) ──

const columnHelper = createColumnHelper<BreakageRowDto>();

/** Stable empty fallback */
const EMPTY_ROWS: BreakageRowDto[] = [];

const COLUMNS = [
  columnHelper.accessor("periodStart", {
    header: "Period Start",
    cell: (info) => formatDateField(info.getValue()),
  }),
  columnHelper.accessor("periodEnd", {
    header: "Period End",
    cell: (info) => formatDateField(info.getValue()),
  }),
  columnHelper.accessor("currencyId", {
    header: "Currency",
    // Use getCurrency for human-readable label (PROJECT-CONTEXT.md: never render raw BE value)
    cell: (info) => getCurrency(info.getValue().toLowerCase()).label,
  }),
  columnHelper.accessor("expiredCount", {
    header: "Expired Count",
    cell: (info) => info.getValue().toLocaleString(),
  }),
  columnHelper.display({
    id: "totalExpiredAmount",
    header: "Total Expired",
    // Currency-aware amount formatting using the row's own currencyId
    cell: ({ row }) =>
      getCurrency(row.original.currencyId.toLowerCase()).rewardFormat(
        row.original.totalExpiredAmount,
      ),
  }),
];

const SKELETON_COL_NAMES = [
  "Period Start",
  "Period End",
  "Currency",
  "Expired Count",
  "Total Expired",
];
const SKELETON_ROW_COUNT = 5;

// ─── Component ────────────────────────────────────────────────────────────────

/**
 * BreakageReportTable
 *
 * Filter bar (date range, currency, granularity) + table with 5 columns +
 * Export CSV button. Covers FE-3 (AC-1, AC-2, AC-3, AC-4, AC-5).
 *
 * UI states:
 *  - Loading: skeleton table rows (AC-1)
 *  - Empty: "No expired balances in this period" (AC-1)
 *  - Error: inline error + retry (AC-1)
 *  - 429 export: toast (AC-3)
 */
export function BreakageReportTable() {
  // ── Filter state ──────────────────────────────────────────────────────────
  const [defaults] = useState(getDefaultDates);
  const [fromValue, setFromValue] = useState(defaults.from);
  const [toValue, setToValue] = useState(defaults.to);
  const [currencyId, setCurrencyId] = useState<string | undefined>(undefined);
  const [granularity, setGranularity] = useState<BreakageGranularity>("MONTH");

  // Applied (committed) filter — the hook re-fetches when this changes
  const [appliedFrom, setAppliedFrom] = useState(defaults.from);
  const [appliedTo, setAppliedTo] = useState(defaults.to);
  const [appliedCurrency, setAppliedCurrency] = useState<string | undefined>(undefined);
  const [appliedGranularity, setAppliedGranularity] = useState<BreakageGranularity>("MONTH");

  // Client-side date validation errors
  const [rangeError, setRangeError] = useState<string | null>(null);

  // Export in-progress guard — ref prevents stale-closure in useCallback dep array
  const isExportingRef = useRef(false);
  const [isExporting, setIsExporting] = useState(false);

  // ── Data fetch ────────────────────────────────────────────────────────────
  const { data, isLoading, isError, refetch } = useBalanceBreakage({
    from: appliedFrom,
    to: appliedTo,
    currencyId: appliedCurrency,
    granularity: appliedGranularity,
  });

  const rows = data?.rows ?? EMPTY_ROWS;

  // ── TanStack Table ────────────────────────────────────────────────────────
  const table = useReactTable({
    data: rows,
    columns: COLUMNS,
    getCoreRowModel: getCoreRowModel(),
  });

  // ── Handlers ──────────────────────────────────────────────────────────────

  /** Validate range and commit applied filters on "Apply filters" */
  const handleApply = useCallback(() => {
    setRangeError(null);

    if (!fromValue || !toValue) {
      setRangeError("Both start and end dates are required");
      return;
    }

    if (toValue < fromValue) {
      setRangeError("End date must be on or after start date");
      return;
    }

    // Validate 24-month cap: count calendar months between dates
    const fromParts = fromValue.split("-").map(Number);
    const toParts = toValue.split("-").map(Number);
    const fy = fromParts[0] ?? 2000;
    const fm = fromParts[1] ?? 1;
    const ty = toParts[0] ?? 2000;
    const tm = toParts[1] ?? 1;
    const monthDiff = (ty - fy) * 12 + (tm - fm);
    if (monthDiff > 24) {
      setRangeError("Range cannot exceed 24 months");
      return;
    }

    setAppliedFrom(fromValue);
    setAppliedTo(toValue);
    setAppliedCurrency(currencyId);
    setAppliedGranularity(granularity);
  }, [fromValue, toValue, currencyId, granularity]);

  /** Export CSV — triggers direct download */
  const handleExport = useCallback(async () => {
    if (isExportingRef.current) return;
    isExportingRef.current = true;
    setIsExporting(true);
    try {
      const blob = await exportBreakage({
        from: appliedFrom,
        to: appliedTo,
        currencyId: appliedCurrency,
        granularity: appliedGranularity,
      });
      // Trigger browser download
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = "balance-expiration-breakage.csv";
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
    } catch (err) {
      if (isAxiosError(err) && err.response?.status === 429) {
        toast.error("You're exporting too frequently. Please wait a moment and try again.");
      } else {
        toast.error("Could not export breakage report. Please try again.");
      }
    } finally {
      isExportingRef.current = false;
      setIsExporting(false);
    }
  }, [appliedFrom, appliedTo, appliedCurrency, appliedGranularity]);

  // ── Render ────────────────────────────────────────────────────────────────
  return (
    <div className="space-y-4">
      {/* Filter bar */}
      <div className="flex flex-wrap items-end gap-3 p-4 rounded-xl border border-border bg-card">
        {/* From date */}
        <div className="flex flex-col gap-1 w-full sm:w-auto">
          <Label htmlFor="breakage-from" className="text-xs text-muted-foreground">
            Start date
          </Label>
          <Input
            id="breakage-from"
            type="date"
            value={fromValue}
            onChange={(e) => setFromValue(e.target.value)}
            className="w-full sm:w-[160px]"
            aria-describedby={rangeError ? "breakage-range-error" : undefined}
          />
        </div>

        {/* To date */}
        <div className="flex flex-col gap-1 w-full sm:w-auto">
          <Label htmlFor="breakage-to" className="text-xs text-muted-foreground">
            End date
          </Label>
          <Input
            id="breakage-to"
            type="date"
            value={toValue}
            onChange={(e) => setToValue(e.target.value)}
            className="w-full sm:w-[160px]"
            aria-describedby={rangeError ? "breakage-range-error" : undefined}
          />
        </div>

        {/* Currency filter */}
        <div className="flex flex-col gap-1 w-full sm:w-auto">
          <Label htmlFor="breakage-currency" className="text-xs text-muted-foreground">Currency</Label>
          <Select
            value={currencyId ?? "all"}
            onValueChange={(v) => setCurrencyId(v === "all" ? undefined : v)}
          >
            <SelectTrigger id="breakage-currency" className="w-full sm:w-[140px]" aria-label="Filter by currency">
              <SelectValue placeholder="All currencies" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All currencies</SelectItem>
              <SelectItem value="cash">Cash</SelectItem>
              <SelectItem value="points">Points</SelectItem>
              <SelectItem value="credits">Credits</SelectItem>
              <SelectItem value="tickets">Tickets</SelectItem>
            </SelectContent>
          </Select>
        </div>

        {/* Granularity */}
        <div className="flex flex-col gap-1 w-full sm:w-auto">
          <Label htmlFor="breakage-granularity" className="text-xs text-muted-foreground">Granularity</Label>
          <Select
            value={granularity}
            onValueChange={(v) => setGranularity(v as BreakageGranularity)}
          >
            <SelectTrigger id="breakage-granularity" className="w-full sm:w-[130px]" aria-label="Select granularity">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="MONTH">Monthly</SelectItem>
              <SelectItem value="QUARTER">Quarterly</SelectItem>
            </SelectContent>
          </Select>
        </div>

        {/* Apply button */}
        <Button
          variant="default"
          onClick={handleApply}
          className="self-end"
        >
          Apply filters
        </Button>
      </div>

      {/* Range validation error */}
      {rangeError && (
        <div
          id="breakage-range-error"
          role="alert"
          aria-live="assertive"
          className="flex items-center gap-2 px-3 py-2 rounded-lg bg-destructive/10 border border-destructive/20 text-sm text-destructive"
        >
          <AlertCircle className="h-4 w-4 shrink-0" aria-hidden="true" />
          {rangeError}
        </div>
      )}

      {/* Table section */}
      {isLoading ? (
        /* Loading skeleton — 5 rows matching column count exactly */
        <div
          role="status"
          aria-busy="true"
          aria-label="Loading breakage report"
          className="rounded-xl border border-border overflow-hidden"
        >
          <div className="overflow-x-auto">
            <Table aria-label="Breakage report loading">
              <TableHeader>
                <TableRow>
                  {SKELETON_COL_NAMES.map((name) => (
                    <TableHead key={name} scope="col">
                      <span className="sr-only">{name}</span>
                      <Skeleton className="h-4 w-20" />
                    </TableHead>
                  ))}
                </TableRow>
              </TableHeader>
              <TableBody>
                {Array.from({ length: SKELETON_ROW_COUNT }, (_, i) => (
                  <TableRow key={i}>
                    {SKELETON_COL_NAMES.map((col) => (
                      <TableCell key={col}>
                        <Skeleton className="h-4 w-24" />
                      </TableCell>
                    ))}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        </div>
      ) : isError ? (
        /* Error state with retry */
        <div
          role="alert"
          aria-live="assertive"
          className="flex flex-col items-center gap-3 py-12 text-center rounded-xl border border-border"
        >
          <AlertCircle className="h-8 w-8 text-destructive" aria-hidden="true" />
          <p className="text-sm text-destructive">Could not load breakage report</p>
          <Button variant="outline" onClick={() => refetch()}>
            Try again
          </Button>
        </div>
      ) : rows.length === 0 ? (
        /* Empty state */
        <div
          role="status"
          aria-live="polite"
          className="flex flex-col items-center gap-3 py-12 text-center rounded-xl border border-border"
        >
          <FileBarChart2 className="h-8 w-8 text-muted-foreground" aria-hidden="true" />
          <p className="text-sm text-muted-foreground">No expired balances in this period</p>
        </div>
      ) : (
        /* Data table */
        <div className="rounded-xl border border-border overflow-hidden">
          {/* Export button — above the table, right-aligned */}
          <div className="flex justify-end p-3 border-b border-border bg-card">
            <Button
              variant="outline"
              size="sm"
              onClick={handleExport}
              disabled={isExporting}
              aria-label="Export breakage report as CSV"
            >
              <Download className="h-4 w-4 mr-2" aria-hidden="true" />
              {isExporting ? "Exporting…" : "Export CSV"}
            </Button>
          </div>

          {/* Horizontally scrollable table — spec: scrolls on narrow viewports */}
          <div className="overflow-x-auto">
            <Table aria-label="Breakage report">
              <TableHeader>
                {table.getHeaderGroups().map((headerGroup) => (
                  <TableRow key={headerGroup.id}>
                    {headerGroup.headers.map((header) => (
                      <TableHead key={header.id} scope="col">
                        {flexRender(
                          header.column.columnDef.header,
                          header.getContext(),
                        )}
                      </TableHead>
                    ))}
                  </TableRow>
                ))}
              </TableHeader>
              <TableBody>
                {table.getRowModel().rows.map((row) => (
                  <TableRow key={row.id}>
                    {row.getVisibleCells().map((cell) => (
                      <TableCell key={cell.id}>
                        {flexRender(
                          cell.column.columnDef.cell,
                          cell.getContext(),
                        )}
                      </TableCell>
                    ))}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        </div>
      )}
    </div>
  );
}
