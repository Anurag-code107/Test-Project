// Adapted from: src/components/redemption-returns/MyReturnsTab.tsx (production analog from Mirror)
import { useState, useRef, useCallback } from "react";
import { InboxIcon, Loader2, MoreHorizontal } from "lucide-react";
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
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { ReturnStatusBadge } from "@/components/redemption-returns/ReturnStatusBadge";
import { ReturnDetailSheet } from "@/components/redemption-returns/ReturnDetailSheet";
import { RejectReturnDialog } from "@/components/redemption-returns/RejectReturnDialog";
import { ResolveTimedOutDialog } from "@/components/redemption-returns/ResolveTimedOutDialog";
import { useAdminReturns } from "@/hooks/useAdminReturns";
import { useApproveReturn } from "@/hooks/useApproveReturn";
import { usePermissions } from "@/hooks/usePermissions";
import { getCurrency } from "@/config/currencies";
import { formatDate } from "@/utils/formatters";
import type { AdminReturnsFilters, ReturnQueueItemResponse, ReturnStatus } from "@/types/redemption-returns.types";

const RETURN_REVIEW_PERMISSION = "action.redemption.return.review";

// Status options for the filter dropdown
const STATUS_OPTIONS: { value: ReturnStatus | "ALL"; label: string }[] = [
  { value: "ALL", label: "All statuses" },
  { value: "PENDING_APPROVAL", label: "Pending Approval" },
  { value: "APPROVED", label: "Approved" },
  { value: "RETURN_CONFIRMED", label: "Confirmed" },
  { value: "RETURN_REJECTED", label: "Rejected" },
  { value: "CANCELLED", label: "Cancelled" },
  { value: "RETURN_TIMED_OUT", label: "Timed Out" },
];

const SKELETON_COLS = 7; // Catalog Item, Partner, Company, Amount, Status, Submitted, Actions

interface ReturnsApprovalTabProps {
  /** clientId is used as the `key` prop by the parent to force remount on tenant switch. */
  clientId: string;
}

// eslint-disable-next-line @typescript-eslint/no-unused-vars
export function ReturnsApprovalTab({ clientId: _clientId }: ReturnsApprovalTabProps) {
  const { can } = usePermissions();
  const canReview = can(RETURN_REVIEW_PERMISSION);

  const [page, setPage] = useState(0);
  const [statusFilter, setStatusFilter] = useState<ReturnStatus | "ALL">("PENDING_APPROVAL");

  // Sheet / dialog state
  const [detailReturnId, setDetailReturnId] = useState<string | null>(null);
  const [approveTargetId, setApproveTargetId] = useState<string | null>(null);
  const [rejectTargetId, setRejectTargetId] = useState<string | null>(null);
  const [resolveTargetId, setResolveTargetId] = useState<string | null>(null);

  const submittingRef = useRef(false);

  const filters: AdminReturnsFilters = {
    ...(statusFilter !== "ALL" ? { status: statusFilter } : {}),
    page,
    size: 20,
  };

  const { data, isLoading, isError, refetch } = useAdminReturns(filters);
  const { mutateAsync: approveMutateAsync, isPending: isApprovePending } = useApproveReturn();

  const rows: ReturnQueueItemResponse[] = data?.data ?? [];
  const pagination = data
    ? {
        page: data.page,
        totalPages: data.totalPages,
        hasNext: data.hasNext,
        hasPrevious: data.hasPrevious,
        totalElements: data.totalElements,
      }
    : null;

  const handleStatusChange = useCallback((value: string) => {
    setStatusFilter(value as ReturnStatus | "ALL");
    setPage(0);
  }, []);

  const handleApproveConfirm = useCallback(async () => {
    if (!approveTargetId || submittingRef.current) return;
    submittingRef.current = true;
    try {
      await approveMutateAsync(approveTargetId);
      setApproveTargetId(null);
    } finally {
      submittingRef.current = false;
    }
  }, [approveTargetId, approveMutateAsync]);

  if (isLoading) {
    return (
      <div
        className="rounded-xl border border-border overflow-hidden"
        role="status"
        aria-busy="true"
        aria-label="Loading return requests"
      >
        <Table>
          <TableHeader>
            <TableRow className="bg-muted/40">
              {["Catalog Item", "Partner", "Company", "Amount", "Status", "Submitted", "Actions"].map((col) => (
                <TableHead key={col} scope="col" className="text-xs font-semibold text-muted-foreground">
                  {col}
                </TableHead>
              ))}
            </TableRow>
          </TableHeader>
          <TableBody>
            {Array.from({ length: 5 }).map((_, i) => (
              <TableRow key={i}>
                {Array.from({ length: SKELETON_COLS }).map((__, j) => (
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

  if (isError) {
    return (
      <div className="flex flex-col items-center justify-center py-20 rounded-xl border border-dashed border-border gap-3">
        <p
          className="text-sm font-medium text-foreground"
          role="alert"
          aria-live="assertive"
        >
          Failed to load return requests.
        </p>
        <Button variant="outline" onClick={() => refetch()}>
          Try again
        </Button>
      </div>
    );
  }

  return (
    <>
      {/* Filter bar */}
      <div className="flex items-center gap-3 mb-4">
        <Select value={statusFilter} onValueChange={handleStatusChange}>
          <SelectTrigger className="w-full max-w-[220px]" aria-label="Filter by status">
            <SelectValue placeholder="Filter by status" />
          </SelectTrigger>
          <SelectContent>
            {STATUS_OPTIONS.map((opt) => (
              <SelectItem key={opt.value} value={opt.value}>
                {opt.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {rows.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-20 rounded-xl border border-dashed border-border">
          <div role="status" className="flex flex-col items-center">
            <InboxIcon className="h-8 w-8 text-muted-foreground mb-3" aria-hidden />
            <p className="text-sm font-medium text-foreground mb-1">No return requests to review.</p>
          </div>
        </div>
      ) : (
        <div className="flex flex-col gap-4">
          <div className="rounded-xl border border-border overflow-x-auto">
            <Table className="min-w-[640px]">
              <TableHeader>
                <TableRow className="bg-muted/40">
                  <TableHead scope="col" className="text-xs font-semibold text-muted-foreground">Catalog Item</TableHead>
                  <TableHead scope="col" className="text-xs font-semibold text-muted-foreground">Partner</TableHead>
                  <TableHead scope="col" className="text-xs font-semibold text-muted-foreground">Company</TableHead>
                  <TableHead scope="col" className="text-xs font-semibold text-muted-foreground">Amount</TableHead>
                  <TableHead scope="col" className="text-xs font-semibold text-muted-foreground">Status</TableHead>
                  <TableHead scope="col" className="text-xs font-semibold text-muted-foreground">Submitted</TableHead>
                  <TableHead scope="col" className="text-xs font-semibold text-muted-foreground text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {rows.map((row) => (
                  <TableRow key={row.id} className="hover:bg-muted/30 transition-colors">
                    <TableCell className="text-sm font-medium text-foreground">
                      {row.catalogItemName}
                    </TableCell>
                    <TableCell className="text-sm text-foreground">
                      {row.partnerDisplayName}
                    </TableCell>
                    <TableCell className="text-sm text-muted-foreground">
                      {row.partnerCompanyName}
                    </TableCell>
                    <TableCell className="text-sm text-foreground tabular-nums">
                      {getCurrency(row.currencyId).rewardFormat(row.amount)}
                    </TableCell>
                    <TableCell>
                      <ReturnStatusBadge status={row.status} />
                    </TableCell>
                    <TableCell className="text-sm text-muted-foreground">
                      {formatDate(row.createdAt)}
                    </TableCell>
                    <TableCell className="text-right" onClick={(e) => e.stopPropagation()}>
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                          <Button
                            variant="ghost"
                            size="icon"
                            className="h-8 w-8"
                            aria-label={`Actions for ${row.catalogItemName}`}
                          >
                            <MoreHorizontal className="h-4 w-4" />
                          </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end">
                          <DropdownMenuItem onClick={() => setDetailReturnId(row.id)}>
                            View Details
                          </DropdownMenuItem>
                          {canReview && row.status === "PENDING_APPROVAL" && (
                            <DropdownMenuItem onClick={() => setApproveTargetId(row.id)}>
                              Approve
                            </DropdownMenuItem>
                          )}
                          {canReview && row.status === "PENDING_APPROVAL" && (
                            <DropdownMenuItem
                              className="text-destructive focus:text-destructive"
                              onClick={() => setRejectTargetId(row.id)}
                            >
                              Reject
                            </DropdownMenuItem>
                          )}
                          {canReview && row.status === "RETURN_TIMED_OUT" && (
                            <DropdownMenuItem onClick={() => setResolveTargetId(row.id)}>
                              Resolve
                            </DropdownMenuItem>
                          )}
                        </DropdownMenuContent>
                      </DropdownMenu>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>

          {pagination && pagination.totalPages > 1 ? (
            <div className="flex items-center justify-between px-1">
              <p className="text-xs text-muted-foreground">
                Page {pagination.page + 1} of {pagination.totalPages}
              </p>
              <div className="flex items-center gap-1.5">
                <Button
                  variant="outline"
                  size="sm"
                  className="h-7 text-xs px-2.5"
                  disabled={!pagination.hasPrevious}
                  onClick={() => setPage((p) => p - 1)}
                >
                  Previous
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  className="h-7 text-xs px-2.5"
                  disabled={!pagination.hasNext}
                  onClick={() => setPage((p) => p + 1)}
                >
                  Next
                </Button>
              </div>
            </div>
          ) : null}
        </div>
      )}

      {/* Approve AlertDialog */}
      <AlertDialog
        open={approveTargetId !== null}
        onOpenChange={(open) => { if (!open && !isApprovePending) setApproveTargetId(null); }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Approve this return request?</AlertDialogTitle>
            <AlertDialogDescription>
              The return will be forwarded to Xoxoday. The partner&apos;s balance will be
              restored only after vendor confirmation.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={isApprovePending}>Cancel</AlertDialogCancel>
            <AlertDialogAction
              onClick={handleApproveConfirm}
              disabled={isApprovePending}
            >
              {isApprovePending ? (
                <Loader2 className="h-4 w-4 animate-spin mr-2" />
              ) : null}
              Approve
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      {/* Reject Dialog */}
      {rejectTargetId && (
        <RejectReturnDialog
          returnId={rejectTargetId}
          open={rejectTargetId !== null}
          onOpenChange={(open) => { if (!open) setRejectTargetId(null); }}
          onSuccess={() => setRejectTargetId(null)}
        />
      )}

      {/* Resolve Timed-Out Dialog */}
      {resolveTargetId !== null ? (
        <ResolveTimedOutDialog
          returnId={resolveTargetId}
          open={true}
          onOpenChange={(open) => { if (!open) setResolveTargetId(null); }}
          onSuccess={() => setResolveTargetId(null)}
        />
      ) : null}

      {/* Return Detail Sheet (admin role) */}
      <ReturnDetailSheet
        returnId={detailReturnId}
        role="admin"
        open={detailReturnId !== null}
        onClose={() => setDetailReturnId(null)}
      />
    </>
  );
}
