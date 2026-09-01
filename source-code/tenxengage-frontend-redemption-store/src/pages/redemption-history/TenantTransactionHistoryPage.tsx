// Adapted from: src/pages/redemption-history/TransactionHistoryPage.tsx (production analog from Mirror)
import { useState, useCallback, useEffect, useMemo } from "react";
import { toast } from "sonner";
import { Download, History, InboxIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
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
import { PageBanner } from "@/components/PageBanner";
import { HistoryFilterBar } from "@/components/redemption-history/HistoryFilterBar";
import { TransactionDetailSheet } from "@/components/redemption-history/TransactionDetailSheet";
import { ExportDialog } from "@/components/redemption-history/ExportDialog";
import { PermissionGate } from "@/components/PermissionGate";
import { useTenantRedemptions } from "@/hooks/redemption-history/useTenantRedemptions";
import { usePermissions } from "@/hooks/usePermissions";
import { cn } from "@/lib/utils";
import { getCurrency } from "@/config/currencies";
import type {
  RedemptionAdminHistoryResponse,
  RedemptionAdminHistoryFilters,
  RedemptionHistoryFilters,
  RedemptionStatus,
} from "@/types/redemption-history/redemption-history.types";

const STATUS_BADGE: Record<RedemptionStatus, { label: string; className: string }> = {
  PENDING_APPROVAL: { label: "Pending", className: "bg-[hsl(38_90%_50%/0.12)] text-amber-600" },
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

interface TenantHistoryTableProps {
  data: RedemptionAdminHistoryResponse[];
  isLoading: boolean;
  onRowClick?: (id: string) => void;
  hasActiveFilters?: boolean;
  pagination?: {
    page: number;
    pageSize: number;
    totalElements: number;
    totalPages: number;
    hasNext: boolean;
    hasPrevious: boolean;
  };
  onPageChange?: (page: number) => void;
}

const COLS = ["Date", "User", "Company", "Item", "Amount", "Status", "Completed"];

function TenantHistoryTable({
  data,
  isLoading,
  onRowClick,
  hasActiveFilters = false,
  pagination,
  onPageChange,
}: TenantHistoryTableProps) {

  if (isLoading) {
    return (
      <div className="rounded-xl border border-border overflow-hidden">
        <Table>
          <TableHeader>
            <TableRow>
              {COLS.map((col) => (
                <TableHead key={col} className="text-xs font-semibold text-muted-foreground">{col}</TableHead>
              ))}
            </TableRow>
          </TableHeader>
          <TableBody>
            {Array.from({ length: 5 }).map((_, i) => (
              <TableRow key={i}>
                {COLS.map((__, j) => (
                  <TableCell key={j}><Skeleton className="h-4 w-full rounded" /></TableCell>
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
        <InboxIcon className="h-8 w-8 text-muted-foreground mb-3" />
        <p className="text-sm font-medium text-foreground mb-1">
          {hasActiveFilters ? "No redemptions match your filters" : "No redemptions yet"}
        </p>
        {hasActiveFilters && (
          <p className="text-sm text-muted-foreground">
            No redemptions match your filters — try adjusting the date range or status filter
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
              {COLS.map((col) => (
                <TableHead key={col} className="text-xs font-semibold text-muted-foreground">{col}</TableHead>
              ))}
            </TableRow>
          </TableHeader>
          <TableBody>
            {data.map((tx) => {
              const badge = STATUS_BADGE[tx.status];
              return (
                <TableRow
                  key={tx.id}
                  className={cn("transition-colors", onRowClick && "hover:bg-muted/30 cursor-pointer")}
                  onClick={onRowClick ? () => onRowClick(tx.id) : undefined}
                  role={onRowClick ? "button" : undefined}
                  tabIndex={onRowClick ? 0 : undefined}
                  onKeyDown={onRowClick ? (e) => { if (e.key === "Enter") { onRowClick(tx.id); } else if (e.key === " ") { e.preventDefault(); onRowClick(tx.id); } } : undefined}
                >
                  <TableCell className="text-sm text-muted-foreground">{formatDate(tx.submittedAt)}</TableCell>
                  <TableCell className="text-sm font-medium text-foreground">{tx.userDisplayName}</TableCell>
                  <TableCell className="text-sm text-muted-foreground">{tx.partnerCompanyName ?? "—"}</TableCell>
                  <TableCell className="text-sm font-medium text-foreground">{tx.catalogItemName}</TableCell>
                  <TableCell className="text-sm text-foreground tabular-nums">
                    {getCurrency(tx.currencyId).rewardFormat(tx.amount)}
                  </TableCell>
                  <TableCell>
                    <Badge variant="outline" className={cn("text-xs font-medium border-0", badge.className)}>
                      {badge.label}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-sm text-muted-foreground">{formatDate(tx.completedAt)}</TableCell>
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

export default function TenantTransactionHistoryPage() {
  const [filters, setFilters] = useState<RedemptionAdminHistoryFilters>({});
  const [userNameInput, setUserNameInput] = useState("");
  const [companyNameInput, setCompanyNameInput] = useState("");
  const [page, setPage] = useState(0);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [exportDialogOpen, setExportDialogOpen] = useState(false);

  const { data, isLoading, isError } = useTenantRedemptions(filters, page);
  const { canAny } = usePermissions();
  // The detail sheet fetches GET /redemption/requests/{id}. By product decision both
  // the tenant auditor (view_all_history) and the history viewer (view_history) may
  // open it, so gate on EITHER permission.
  // NOTE (backend/contract): the detail endpoint must accept
  // action.redemption.view_all_history in addition to action.redemption.view_history
  // — redemption-history.yaml currently lists only view_history. Owned by BE.
  const canViewDetail = canAny(
    "action.redemption.view_history",
    "action.redemption.view_all_history",
  );

  useEffect(() => {
    if (isError) toast.error("Could not load redemptions");
  }, [isError]);

  const hasActiveFilters =
    !!filters.dateFrom || !!filters.dateTo || !!filters.status ||
    !!filters.category || !!filters.userName || !!filters.companyName;

  const handleBaseFiltersChange = useCallback((base: RedemptionHistoryFilters) => {
    setFilters((prev) => ({
      dateFrom: base.dateFrom,
      dateTo: base.dateTo,
      status: base.status,
      category: base.category,
      sortBy: base.sortBy,
      sortDirection: base.sortDirection,
      userName: prev.userName,
      companyName: prev.companyName,
    }));
    setPage(0);
  }, []);

  const applyUserNameFilter = useCallback(() => {
    setFilters((prev) => ({ ...prev, userName: userNameInput.trim() || undefined }));
    setPage(0);
  }, [userNameInput]);

  const applyCompanyNameFilter = useCallback(() => {
    setFilters((prev) => ({ ...prev, companyName: companyNameInput.trim() || undefined }));
    setPage(0);
  }, [companyNameInput]);

  const clearAllFilters = useCallback(() => {
    setFilters({});
    setUserNameInput("");
    setCompanyNameInput("");
    setPage(0);
  }, []);

  const handleRowClick = useCallback((id: string) => setSelectedId(id), []);
  const handlePageChange = useCallback((p: number) => setPage(p), []);

  const rows = data?.data ?? [];
  const pagination = useMemo(
    () => data
      ? { page: data.page, pageSize: data.pageSize, totalElements: data.totalElements,
          totalPages: data.totalPages, hasNext: data.hasNext, hasPrevious: data.hasPrevious }
      : undefined,
    [data],
  );

  const exportFilters = useMemo(
    (): RedemptionHistoryFilters & { userName?: string; companyName?: string } => ({
      dateFrom: filters.dateFrom,
      dateTo: filters.dateTo,
      status: filters.status,
      category: filters.category,
      userName: filters.userName,
      companyName: filters.companyName,
    }),
    [filters.dateFrom, filters.dateTo, filters.status, filters.category, filters.userName, filters.companyName],
  );

  return (
    <div className="flex flex-col h-full animate-route-in" aria-label="Main content">
      <div className="shrink-0 mb-6">
        <PageBanner
          theme="default"
          title="All redemption history"
          subtitle="Every redemption across your organization"
          actions={
            <div className="flex items-center gap-2">
              <History className="h-4 w-4 text-muted-foreground" />
              {pagination && (
                <span className="text-sm text-muted-foreground tabular-nums">
                  {pagination.totalElements} transactions
                </span>
              )}
              <PermissionGate permission="action.redemption.export">
                <Button variant="outline" size="sm" onClick={() => setExportDialogOpen(true)}>
                  <Download className="h-4 w-4 mr-2" />
                  Export
                </Button>
              </PermissionGate>
            </div>
          }
        />

        <div className="mt-5 space-y-3">
          <HistoryFilterBar
            filters={{
              dateFrom: filters.dateFrom,
              dateTo: filters.dateTo,
              status: filters.status,
              category: filters.category,
              sortBy: filters.sortBy,
              sortDirection: filters.sortDirection,
            }}
            onFiltersChange={handleBaseFiltersChange}
          />
          <div className="flex flex-wrap items-end gap-3">
            <div className="flex flex-col gap-1">
              <Label htmlFor="userName-filter" className="text-xs text-muted-foreground">User name</Label>
              <div className="flex gap-1">
                <Input
                  id="userName-filter"
                  placeholder="e.g. Alice Smith"
                  value={userNameInput}
                  onChange={(e) => setUserNameInput(e.target.value)}
                  onKeyDown={(e) => e.key === "Enter" && applyUserNameFilter()}
                  className="h-9 w-64 text-sm"
                  aria-label="User name filter"
                />
                <Button variant="outline" size="sm" className="h-9" onClick={applyUserNameFilter}>
                  Apply
                </Button>
              </div>
            </div>
            <div className="flex flex-col gap-1">
              <Label htmlFor="companyName-filter" className="text-xs text-muted-foreground">Company name</Label>
              <div className="flex gap-1">
                <Input
                  id="companyName-filter"
                  placeholder="e.g. Acme Corp"
                  value={companyNameInput}
                  onChange={(e) => setCompanyNameInput(e.target.value)}
                  onKeyDown={(e) => e.key === "Enter" && applyCompanyNameFilter()}
                  className="h-9 w-64 text-sm"
                  aria-label="Company name filter"
                />
                <Button variant="outline" size="sm" className="h-9" onClick={applyCompanyNameFilter}>
                  Apply
                </Button>
              </div>
            </div>
            {hasActiveFilters && (
              <Button variant="ghost" size="sm" className="h-9 self-end text-muted-foreground hover:text-foreground" onClick={clearAllFilters}>
                Clear all
              </Button>
            )}
          </div>
        </div>
      </div>

      <div className="flex-1 min-h-0 overflow-y-auto">
        <TenantHistoryTable
          data={rows}
          isLoading={isLoading}
          onRowClick={canViewDetail ? handleRowClick : undefined}
          hasActiveFilters={hasActiveFilters}
          pagination={pagination}
          onPageChange={handlePageChange}
        />
      </div>

      {canViewDetail && (
        <TransactionDetailSheet
          redemptionId={selectedId}
          open={selectedId !== null}
          onClose={() => setSelectedId(null)}
        />
      )}
      <ExportDialog
        open={exportDialogOpen}
        onClose={() => setExportDialogOpen(false)}
        filters={exportFilters}
        scope="ALL_TENANT"
      />
    </div>
  );
}
