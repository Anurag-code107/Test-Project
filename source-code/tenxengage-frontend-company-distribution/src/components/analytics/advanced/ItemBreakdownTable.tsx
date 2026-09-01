// shape: contracts/models/redemption-advanced-analytics.md → ItemBreakdownResponse
// Adapted from: src/components/redemption-history/TransactionHistoryTable.tsx (shadcn Table + getCurrency pattern)
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
import type { ItemRedemptionDto } from "@/types/redemption-analytics-advanced.types";

export interface ItemBreakdownTableProps {
  items: ItemRedemptionDto[] | undefined;
  lastRefreshedAt: string | undefined;
  isLoading: boolean;
  isError: boolean;
  refetch: () => void;
}

const columnHelper = createColumnHelper<ItemRedemptionDto>();

/** Stable empty array — avoids creating a new reference on every render when items is undefined */
const EMPTY_ITEMS: ItemRedemptionDto[] = [];

/** Month abbreviations hoisted to module scope to avoid re-creation on every caption render */
const MONTH_NAMES = ["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"];

const COLUMNS = [
  columnHelper.accessor("catalogItemName", {
    header: "Item Name",
    cell: (info) => info.getValue(),
  }),
  columnHelper.accessor("currencyId", {
    header: "Currency",
    // Normalise to lowercase before lookup — BE emits "POINTS", registry keys are lowercase
    cell: (info) => getCurrency(info.getValue().toLowerCase()).label,
  }),
  columnHelper.accessor("totalRedeemedCount", {
    header: "Redeemed Count",
    cell: (info) => info.getValue().toLocaleString(),
  }),
  columnHelper.display({
    id: "totalRedeemedAmount",
    header: "Amount",
    // Display column allows access to row.original for per-row currency-aware formatting
    cell: ({ row }) =>
      getCurrency(row.original.currencyId.toLowerCase()).rewardFormat(
        row.original.totalRedeemedAmount,
      ),
  }),
  columnHelper.accessor("redemptionRate", {
    header: "Rate (%)",
    cell: (info) => `${info.getValue().toFixed(2)}%`,
  }),
];

/** 5 skeleton rows shown during loading — matches spec FE-3 */
const SKELETON_ROW_COUNT = 5;
const SKELETON_COLS = ["Item Name", "Currency", "Redeemed Count", "Amount", "Rate (%)"];

/**
 * Format the "Data as of {date} at {time} UTC" caption.
 * Both date and time are derived from the UTC representation (toISOString) so the
 * caption is consistent regardless of the browser's local timezone.
 * Anti-pattern avoided: formatDate() uses date-fns in the local timezone, which
 * produces an incorrect next-day date for users east of UTC near midnight UTC.
 */
function formatCaption(lastRefreshedAt: string): string {
  const iso = new Date(lastRefreshedAt).toISOString(); // e.g. "2026-06-20T06:00:00.000Z"
  const datePart = iso.slice(0, 10);                   // "2026-06-20"
  const timePart = iso.slice(11, 16);                  // "06:00"
  // Format date as "Jun 20, 2026" (locale-safe from UTC string)
  const [year, month, day] = datePart.split("-").map(Number) as [number, number, number];
  const formattedDate = `${MONTH_NAMES[month - 1]} ${day}, ${year}`;
  return `Data as of ${formattedDate} at ${timePart} UTC`;
}

/**
 * ItemBreakdownTable — renders the FR-08.1 item redemption breakdown.
 *
 * Sort: server-side descending by totalRedeemedCount (BE returns pre-sorted rows).
 * States: loading (5 skeleton rows), empty ("No data for the selected period"),
 *         error ("Unable to load item breakdown" + Retry button), data.
 */
export function ItemBreakdownTable({
  items,
  lastRefreshedAt,
  isLoading,
  isError,
  refetch,
}: ItemBreakdownTableProps) {
  const table = useReactTable({
    data: items ?? EMPTY_ITEMS,
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
        <p className="text-sm text-destructive">Unable to load item breakdown</p>
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
        aria-label="Loading Item Breakdown"
        className="rounded-lg border"
      >
        <Table>
          <TableHeader>
            <TableRow>
              {SKELETON_COLS.map((h) => (
                <TableHead key={h} scope="col">
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
  if (!items || items.length === 0) {
    return (
      <div role="status" className="flex flex-col items-center gap-2 py-10 text-center">
        <p className="text-sm text-muted-foreground">No data for the selected period</p>
      </div>
    );
  }

  // ── Data table ────────────────────────────────────────────────────────────────
  return (
    <div className="space-y-2">
      <div className="rounded-lg border">
        <Table>
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

      {/* "Data as of …" caption (AC-5, FR-08.8) */}
      {lastRefreshedAt && (
        <p className="text-xs text-muted-foreground">
          <time dateTime={lastRefreshedAt}>{formatCaption(lastRefreshedAt)}</time>
        </p>
      )}
    </div>
  );
}
