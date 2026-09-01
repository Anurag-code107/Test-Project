// Adapted from: src/pages/client-admin/ManageIncentivesPage.tsx (production analog from Mirror)
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
import { CheckCircle, XCircle, InboxIcon } from "lucide-react";
import type { ApprovalQueueItem, PaginationMeta } from "@/types/redemption/redemption.types";
import { getCurrency } from "@/config/currencies";
import { cn } from "@/lib/utils";

interface ApprovalQueueTableProps {
  items: ApprovalQueueItem[];
  pagination: PaginationMeta;
  onApprove: (id: string) => void;
  onReject: (id: string) => void;
  isLoading: boolean;
  onPageChange?: (page: number) => void;
}

function formatAmount(amount: string, currencyId: string): string {
  return getCurrency(currencyId).format(amount);
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
}

export function ApprovalQueueTable({
  items,
  pagination,
  onApprove,
  onReject,
  isLoading,
  onPageChange,
}: ApprovalQueueTableProps) {
  if (isLoading) {
    return (
      <div className="rounded-xl border border-border overflow-hidden">
        <Table>
          <TableHeader>
            <TableRow>
              {["Requester", "Item", "Currency", "Amount", "Wallet", "Submitted", "Actions"].map(
                (col) => (
                  <TableHead key={col} className="text-xs font-semibold text-muted-foreground">
                    {col}
                  </TableHead>
                ),
              )}
            </TableRow>
          </TableHeader>
          <TableBody>
            {Array.from({ length: 5 }).map((_, i) => (
              <TableRow key={i}>
                {Array.from({ length: 7 }).map((__, j) => (
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

  if (items.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-20 rounded-xl border border-dashed border-border">
        <InboxIcon className="h-8 w-8 text-muted-foreground mb-3" />
        <p className="text-sm font-medium text-foreground mb-1">No pending redemptions</p>
        <p className="text-sm text-muted-foreground">No redemption requests are pending approval.</p>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="rounded-xl border border-border overflow-x-auto">
        <Table>
          <TableHeader>
            <TableRow className="bg-muted/40">
              <TableHead className="text-xs font-semibold text-muted-foreground">Requester</TableHead>
              <TableHead className="text-xs font-semibold text-muted-foreground">Item</TableHead>
              <TableHead className="text-xs font-semibold text-muted-foreground">Currency</TableHead>
              <TableHead className="text-xs font-semibold text-muted-foreground">Amount</TableHead>
              <TableHead className="text-xs font-semibold text-muted-foreground">Wallet</TableHead>
              <TableHead className="text-xs font-semibold text-muted-foreground">Submitted</TableHead>
              <TableHead className="text-xs font-semibold text-muted-foreground text-right">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {items.map((item) => (
              <TableRow key={item.id} className="hover:bg-muted/30 transition-colors">
                <TableCell className="text-sm font-medium text-foreground">
                  {item.requestingUserDisplayName}
                </TableCell>
                <TableCell className="text-sm text-foreground">{item.catalogItemName}</TableCell>
                <TableCell>
                  <span className="inline-flex items-center gap-1 text-xs font-medium px-2 py-0.5 rounded-full bg-muted text-muted-foreground">
                    {item.currencyId.toUpperCase()}
                  </span>
                </TableCell>
                <TableCell className="text-sm text-foreground tabular-nums">
                  {formatAmount(item.amount, item.currencyId)}
                </TableCell>
                <TableCell>
                  <span
                    className={cn(
                      "text-xs font-medium px-2 py-0.5 rounded-full",
                      item.walletType === "INDIVIDUAL"
                        ? "bg-primary/10 text-primary"
                        : "bg-[hsl(245_58%_55%/0.1)] text-[hsl(245_58%_45%)]",
                    )}
                  >
                    {item.walletType === "INDIVIDUAL" ? "Individual" : "Company"}
                  </span>
                </TableCell>
                <TableCell className="text-sm text-muted-foreground">
                  {formatDate(item.submittedAt)}
                </TableCell>
                <TableCell className="text-right">
                  <div
                    className="flex items-center justify-end gap-1.5"
                    onClick={(e) => e.stopPropagation()}
                  >
                    <Button
                      variant="outline"
                      size="sm"
                      className="h-7 text-xs px-2.5 border-success/30 text-success hover:bg-success/5"
                      aria-label={`Approve redemption for ${item.requestingUserDisplayName}`}
                      onClick={() => onApprove(item.id)}
                    >
                      <CheckCircle className="h-3 w-3 mr-1" />
                      Approve
                    </Button>
                    <Button
                      variant="outline"
                      size="sm"
                      className="h-7 text-xs px-2.5 border-destructive/30 text-destructive hover:bg-destructive/5"
                      aria-label={`Reject redemption for ${item.requestingUserDisplayName}`}
                      onClick={() => onReject(item.id)}
                    >
                      <XCircle className="h-3 w-3 mr-1" />
                      Reject
                    </Button>
                  </div>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>

      {pagination.totalPages > 1 ? (
        <div className="flex items-center justify-between px-1">
          <p className="text-xs text-muted-foreground">
            Showing {pagination.page * pagination.pageSize + 1}–
            {Math.min((pagination.page + 1) * pagination.pageSize, pagination.totalElements)} of{" "}
            {pagination.totalElements} requests
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
