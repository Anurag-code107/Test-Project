// Adapted from: src/pages/client-admin/ManageIncentivesPage.tsx (production analog from Mirror)
import { InboxIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Skeleton } from "@/components/ui/skeleton";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import { getCurrency } from "@/config/currencies";
import type { RedemptionRequestResponse, RedemptionStatus } from "@/types/redemption-history/redemption-history.types";

interface TransactionHistoryTableProps {
  data: RedemptionRequestResponse[];
  isLoading: boolean;
  onRowClick: (id: string) => void;
  hasActiveFilters?: boolean;
  emptyNoFiltersText?: string;
  emptyWithFiltersText?: string;
  pagination?: {
    page: number;
    pageSize: number;
    totalElements: number;
    totalPages: number;
    hasNext: boolean;
    hasPrevious: boolean;
  };
  onPageChange?: (page: number) => void;
  /** F-06: callback for "Request Return" action on eligible rows */
  onRequestReturn?: (tx: RedemptionRequestResponse) => void;
}

const STATUS_BADGE: Record<RedemptionStatus, { label: string; className: string }> = {
  PENDING_APPROVAL: { label: "Pending", className: "bg-warning/10 text-warning" },
  RESERVED:         { label: "Reserved", className: "bg-primary/10 text-primary" },
  PROCESSING:       { label: "Processing", className: "bg-primary/10 text-primary" },
  COMPLETED:        { label: "Completed", className: "bg-success/10 text-success" },
  FAILED:           { label: "Failed", className: "bg-destructive/10 text-destructive" },
  CANCELLED:        { label: "Cancelled", className: "bg-muted text-muted-foreground" },
};

function formatDate(iso?: string): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleDateString(undefined, { year: "numeric", month: "short", day: "numeric" });
}

function formatAmount(amount: string, currencyId: string): string {
  return getCurrency(currencyId).rewardFormat(amount);
}

export function TransactionHistoryTable({
  data,
  isLoading,
  onRowClick,
  hasActiveFilters = false,
  emptyNoFiltersText = "No transactions yet",
  emptyWithFiltersText = "No transactions match your filters",
  pagination,
  onPageChange,
  onRequestReturn,
}: TransactionHistoryTableProps) {
  if (isLoading) {
    const skeletonCols = onRequestReturn
      ? ["Date", "Item", "Amount", "Status", "Completed", "Actions"]
      : ["Date", "Item", "Amount", "Status", "Completed"];
    return (
      <div className="rounded-xl border border-border overflow-hidden">
        <Table>
          <TableHeader>
            <TableRow>
              {skeletonCols.map((col) => (
                <TableHead key={col} className="text-xs font-semibold text-muted-foreground">
                  {col}
                </TableHead>
              ))}
            </TableRow>
          </TableHeader>
          <TableBody>
            {Array.from({ length: 5 }).map((_, i) => (
              <TableRow key={i}>
                {Array.from({ length: skeletonCols.length }).map((__, j) => (
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

  if (data.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-20 rounded-xl border border-dashed border-border">
        <InboxIcon className="h-8 w-8 text-muted-foreground mb-3" aria-hidden="true" />
        <p className="text-sm font-medium text-foreground mb-1">
          {hasActiveFilters ? emptyWithFiltersText : emptyNoFiltersText}
        </p>
        {hasActiveFilters && (
          <p className="text-sm text-muted-foreground">
            Try adjusting the date range or status filter
          </p>
        )}
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="rounded-xl border border-border overflow-x-auto">
        <Table>
          <TableHeader>
            <TableRow className="bg-muted/40">
              <TableHead scope="col" className="text-xs font-semibold text-muted-foreground">Date</TableHead>
              <TableHead scope="col" className="text-xs font-semibold text-muted-foreground">Item</TableHead>
              <TableHead scope="col" className="text-xs font-semibold text-muted-foreground">Amount</TableHead>
              <TableHead scope="col" className="text-xs font-semibold text-muted-foreground">Status</TableHead>
              <TableHead scope="col" className="text-xs font-semibold text-muted-foreground">Completed</TableHead>
              {onRequestReturn ? (
                <TableHead scope="col" className="text-xs font-semibold text-muted-foreground">Actions</TableHead>
              ) : null}
            </TableRow>
          </TableHeader>
          <TableBody>
            {data.map((tx) => {
              const badge = STATUS_BADGE[tx.status];
              return (
                <TableRow
                  key={tx.id}
                  className="hover:bg-muted/30 transition-colors cursor-pointer"
                  onClick={() => onRowClick(tx.id)}
                  role="button"
                  tabIndex={0}
                  onKeyDown={(e) => { if (e.key === "Enter") { onRowClick(tx.id); } else if (e.key === " ") { e.preventDefault(); onRowClick(tx.id); } }}
                >
                  <TableCell className="text-sm text-muted-foreground">
                    {formatDate(tx.submittedAt)}
                  </TableCell>
                  <TableCell className="text-sm font-medium text-foreground">
                    {tx.catalogItemName}
                  </TableCell>
                  <TableCell className="text-sm text-foreground tabular-nums">
                    {formatAmount(tx.amount, tx.currencyId)}
                  </TableCell>
                  <TableCell>
                    <Badge
                      variant="outline"
                      className={cn("text-xs font-medium border-0", badge.className)}
                    >
                      {badge.label}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-sm text-muted-foreground">
                    {formatDate(tx.completedAt)}
                  </TableCell>
                  {onRequestReturn ? (
                    <TableCell onClick={(e) => e.stopPropagation()}>
                      {tx.category === "CASH" ? (
                        // CASH (bank transfer / cash gift card) isn't returnable.
                        <span className="text-xs text-muted-foreground">N/A</span>
                      ) : tx.isReturnEligible ? (
                        <Button
                          variant="ghost"
                          size="sm"
                          className="h-7 text-xs px-2.5"
                          aria-label={`Request Return for ${tx.catalogItemName}`}
                          onClick={() => onRequestReturn(tx)}
                        >
                          Request Return
                        </Button>
                      ) : null}
                    </TableCell>
                  ) : null}
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </div>

      {pagination && pagination.totalPages > 1 ? (
        <div className="flex items-center justify-between px-1">
          <p className="text-xs text-muted-foreground">
            Showing {pagination.page * pagination.pageSize + 1}–
            {Math.min((pagination.page + 1) * pagination.pageSize, pagination.totalElements)} of{" "}
            {pagination.totalElements} transactions
          </p>
          <div className="flex items-center gap-1.5">
            <Button
              variant="outline"
              size="sm"
              className="h-7 text-xs px-2.5"
              disabled={!pagination.hasPrevious}
              onClick={() => onPageChange?.(pagination.page - 1)}
            >
              Previous
            </Button>
            <Button
              variant="outline"
              size="sm"
              className="h-7 text-xs px-2.5"
              disabled={!pagination.hasNext}
              onClick={() => onPageChange?.(pagination.page + 1)}
            >
              Next
            </Button>
          </div>
        </div>
      ) : null}
    </div>
  );
}
