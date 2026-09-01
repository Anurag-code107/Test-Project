// shape: contracts/models/redemption-advanced-analytics.md → FailureBreakdownResponse
// Adapted from: src/components/analytics/advanced/ItemBreakdownTable.tsx (TanStack Table + shadcn pattern)
// Covers: AC-2, AC-4 (story US-07)
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
import { getCurrency } from "@/config/currencies";
import type { FailureModeDto } from "@/types/redemption-analytics-advanced.types";

export interface FailureBreakdownTableProps {
  failureModes: FailureModeDto[] | undefined;
  lastRefreshedAt: string | undefined;
  isLoading: boolean;
  isError: boolean;
  refetch: () => void;
}

const columnHelper = createColumnHelper<FailureModeDto>();

/** Stable empty array — avoids creating a new reference on every render */
const EMPTY_FAILURE_MODES: FailureModeDto[] = [];

/** Month abbreviations hoisted to module scope to avoid re-creation on every caption render */
const MONTH_NAMES = ["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"];

/**
 * Map processingMode enum values to display strings.
 * Spec (AC-4): "Manual" for MANUAL, "Automated" for AUTOMATED.
 * Spec (spec.md Functional Completeness Audit ⊕-8): The corrected enum values from
 * F-03 RedemptionProcessingMode are INSTANT | BATCH | APPROVAL_REQUIRED.
 * The story E2E fixtures use MANUAL/AUTOMATED as mock values; display mapping covers both sets.
 */
const PROCESSING_MODE_LABELS: Record<string, string> = {
  MANUAL: "Manual",
  AUTOMATED: "Automated",
  INSTANT: "Instant",
  BATCH: "Batch",
  APPROVAL_REQUIRED: "Approval Required",
};

function formatProcessingMode(value: string): string {
  return PROCESSING_MODE_LABELS[value] ?? value;
}

const COLUMNS = [
  columnHelper.accessor("processingMode", {
    header: "Processing Mode",
    cell: (info) => formatProcessingMode(info.getValue()),
  }),
  columnHelper.accessor("catalogItemName", {
    header: "Item Name",
    cell: (info) => info.getValue(),
  }),
  columnHelper.accessor("currencyId", {
    header: "Currency",
    cell: (info) => getCurrency(info.getValue().toLowerCase()).label,
  }),
  columnHelper.accessor("failedCount", {
    header: "Failed",
    cell: (info) => info.getValue().toLocaleString(),
  }),
  columnHelper.accessor("cancelledCount", {
    header: "Cancelled",
    cell: (info) => info.getValue().toLocaleString(),
  }),
  columnHelper.accessor("totalCount", {
    header: "Total",
    cell: (info) => info.getValue().toLocaleString(),
  }),
  columnHelper.accessor("failureRate", {
    header: "Failure Rate (%)",
    // Formatted as percent with 1 decimal (e.g. "23.5%") — spec AC-4 / FE-3
    // failureRate from BE is a percentage (0–100) per contracts/models/redemption-advanced-analytics.md
    // (same range as ItemBreakdownTable.redemptionRate — no × 100 needed)
    cell: (info) => `${info.getValue().toFixed(1)}%`,
  }),
];

/** 5 skeleton rows shown during loading — spec FE-3 */
const SKELETON_ROW_COUNT = 5;
const SKELETON_COLS = [
  "Processing Mode",
  "Item Name",
  "Currency",
  "Failed",
  "Cancelled",
  "Total",
  "Failure Rate (%)",
];

/**
 * Format the "Data as of {date} at {time} UTC" caption.
 * UTC-based to avoid timezone-shifted dates for users east of UTC.
 * Anti-pattern avoided: using toISOString() (UTC) rather than local date methods.
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
 * FailureBreakdownTable — renders the FR-08.7 failure mode breakdown.
 *
 * Sort: server-side descending by failureRate (BE returns pre-sorted rows).
 * States: loading (5 skeleton rows), empty ("No data for the selected period"),
 *         error ("Unable to load failure breakdown" + Retry button), data.
 * Columns: Processing Mode, Item Name, Currency, Failed, Cancelled, Total, Failure Rate (%)
 * Caption: "Data as of {date} at {time} UTC" (FR-08.8).
 */
export function FailureBreakdownTable({
  failureModes,
  lastRefreshedAt,
  isLoading,
  isError,
  refetch,
}: FailureBreakdownTableProps) {
  const table = useReactTable({
    data: failureModes ?? EMPTY_FAILURE_MODES,
    columns: COLUMNS,
    getCoreRowModel: getCoreRowModel(),
  });

  // ── Error state ──────────────────────────────────────────────────────────────
  if (isError) {
    return (
      <div
        role="alert"
        aria-live="assertive"
        className="flex flex-col items-center gap-3 rounded-lg border border-destructive/20 bg-destructive/5 px-6 py-8 text-center"
      >
        <p className="text-sm text-destructive">Unable to load failure breakdown</p>
        <Button variant="outline" size="sm" onClick={refetch}>
          Retry
        </Button>
      </div>
    );
  }

  // ── Loading skeleton (5 rows) ────────────────────────────────────────────────
  if (isLoading) {
    return (
      <div
        role="status"
        aria-busy="true"
        aria-label="Loading Failure Breakdown"
        className="rounded-lg border"
      >
        <Table>
          <TableHeader>
            <TableRow>
              {SKELETON_COLS.map((h) => (
                <TableHead key={h} scope="col">
                  <span className="sr-only">{h}</span>
                  <Skeleton className="h-4 w-full rounded" />
                </TableHead>
              ))}
            </TableRow>
          </TableHeader>
          <TableBody>
            {Array.from({ length: SKELETON_ROW_COUNT }, (_, i) => (
              <TableRow key={i}>
                {Array.from({ length: SKELETON_COLS.length }, (_, j) => (
                  <TableCell key={j}>
                    <Skeleton className="h-4 w-full rounded" />
                  </TableCell>
                ))}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
    );
  }

  // ── Empty state ───────────────────────────────────────────────────────────────
  if (!failureModes || failureModes.length === 0) {
    return (
      <div role="status" aria-live="polite" className="flex flex-col items-center gap-2 py-10 text-center">
        <p className="text-sm text-muted-foreground">No data for the selected period</p>
      </div>
    );
  }

  // ── Data table ────────────────────────────────────────────────────────────────
  return (
    <div className="space-y-2">
      <div className="rounded-lg border">
        <Table aria-label="Failure Breakdown">
          <TableHeader>
            {table.getHeaderGroups().map((headerGroup) => (
              <TableRow key={headerGroup.id}>
                {headerGroup.headers.map((header) => (
                  <TableHead key={header.id} scope="col">
                    {flexRender(header.column.columnDef.header, header.getContext())}
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
                    {flexRender(cell.column.columnDef.cell, cell.getContext())}
                  </TableCell>
                ))}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>

      {/* "Data as of …" caption (AC-4, FR-08.8) */}
      {lastRefreshedAt ? (
        <p className="text-xs text-muted-foreground">
          <time dateTime={lastRefreshedAt}>{formatCaption(lastRefreshedAt)}</time>
        </p>
      ) : null}
    </div>
  );
}
