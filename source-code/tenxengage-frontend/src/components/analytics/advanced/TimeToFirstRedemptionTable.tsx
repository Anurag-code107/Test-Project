// Adapted from: src/components/analytics/advanced/SegmentBreakdownTable.tsx (same domain, same states pattern)
// shape: contracts/models/redemption-advanced-analytics.md -> TimeToFirstRedemptionResponse
// Covers: AC-2, AC-4 (story US-03)
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
import type { RegionTimeToRedemptionDto } from "@/types/redemption-analytics-advanced.types";

export interface TimeToFirstRedemptionTableProps {
  regions: RegionTimeToRedemptionDto[] | undefined;
  lastRefreshedAt: string | undefined;
  isLoading: boolean;
  isError: boolean;
  refetch: () => void;
}

const columnHelper = createColumnHelper<RegionTimeToRedemptionDto>();

/** Stable empty array -- avoids creating a new reference on every render when regions is undefined */
const EMPTY_REGIONS: RegionTimeToRedemptionDto[] = [];

/** Month abbreviations hoisted to module scope to avoid re-creation on every caption render */
const MONTH_NAMES = ["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"];

const COLUMNS = [
  columnHelper.accessor("region", {
    header: "Region",
    // null region -> "--" per AC-4 verbatim microcopy
    cell: (info) => info.getValue() ?? "—",
  }),
  columnHelper.accessor("avgHoursToFirstRedemption", {
    header: "Avg Hours",
    // null avg when sampleCount=0 -> "N/A" per AC-2 + AC-4 verbatim microcopy
    cell: (info) => {
      const value = info.getValue();
      return value === null ? "N/A" : value.toFixed(1);
    },
  }),
  columnHelper.accessor("medianHoursToFirstRedemption", {
    header: "Median Hours",
    // null median when sampleCount=0 -> "N/A" per AC-2 + AC-4 verbatim microcopy
    cell: (info) => {
      const value = info.getValue();
      return value === null ? "N/A" : value.toFixed(1);
    },
  }),
  columnHelper.accessor("sampleCount", {
    header: "Sample Count",
    cell: (info) => info.getValue().toLocaleString(),
  }),
];

/** 3 skeleton rows shown during loading -- matches US-03 UI States spec */
const SKELETON_ROW_COUNT = 3;
const SKELETON_COLS = ["Region", "Avg Hours", "Median Hours", "Sample Count"];

/**
 * Format the "Data as of {date} at {time} UTC" caption (FR-08.8).
 * Both date and time are derived from the UTC representation (toISOString) so the
 * caption is consistent regardless of the browser's local timezone.
 * Anti-pattern avoided: formatDate() uses date-fns in local timezone, producing an
 * incorrect next-day date for users east of UTC near midnight UTC.
 */
function formatCaption(lastRefreshedAt: string): string {
  const iso = new Date(lastRefreshedAt).toISOString();
  const datePart = iso.slice(0, 10);
  const timePart = iso.slice(11, 16);
  const [year, month, day] = datePart.split("-").map(Number) as [number, number, number];
  const formattedDate = `${MONTH_NAMES[month - 1]} ${day}, ${year}`;
  return `Data as of ${formattedDate} at ${timePart} UTC`;
}

/**
 * TimeToFirstRedemptionTable -- renders the FR-08.3 time-to-first-redemption summary.
 *
 * Columns per AC-4: Region, Avg Hours, Median Hours, Sample Count.
 * Null avg/median cells render "N/A" per AC-2 + AC-4 verbatim microcopy.
 * Null region cells render "--" per AC-4 verbatim microcopy.
 * States: loading (3 skeleton rows), empty ("No data for the selected period"),
 *         error ("Unable to load time-to-first-redemption data" + Retry button), data.
 */
export function TimeToFirstRedemptionTable({
  regions,
  lastRefreshedAt,
  isLoading,
  isError,
  refetch,
}: TimeToFirstRedemptionTableProps) {
  const table = useReactTable({
    data: regions ?? EMPTY_REGIONS,
    columns: COLUMNS,
    getCoreRowModel: getCoreRowModel(),
  });

  if (isError) {
    return (
      <div
        role="alert"
        aria-live="assertive"
        className="flex flex-col items-center gap-3 rounded-lg border border-destructive/20 bg-destructive/5 px-6 py-8 text-center"
      >
        <p className="text-sm text-destructive">Unable to load time-to-first-redemption data</p>
        <Button variant="outline" size="sm" onClick={refetch}>
          Retry
        </Button>
      </div>
    );
  }

  if (isLoading) {
    return (
      <div
        role="status"
        aria-busy="true"
        aria-label="Loading Time to First Redemption"
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

  if (!regions || regions.length === 0) {
    return (
      <div role="status" aria-live="polite" className="flex flex-col items-center gap-2 py-10 text-center">
        <p className="text-sm text-muted-foreground">No data for the selected period</p>
      </div>
    );
  }

  return (
    <div className="space-y-2">
      <div className="rounded-lg border">
        <Table aria-label="Time to First Redemption">
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

      {lastRefreshedAt && (
        <p className="text-xs text-muted-foreground">
          <time dateTime={lastRefreshedAt}>{formatCaption(lastRefreshedAt)}</time>
        </p>
      )}
    </div>
  );
}
