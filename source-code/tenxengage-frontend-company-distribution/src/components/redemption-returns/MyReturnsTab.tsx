// Adapted from: src/components/redemption/ApprovalQueueTable.tsx (production analog from Mirror)
import { useState, useRef, useCallback } from "react";
import { InboxIcon } from "lucide-react";
import { Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
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
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { ReturnStatusBadge } from "@/components/redemption-returns/ReturnStatusBadge";
import { ReturnDetailSheet } from "@/components/redemption-returns/ReturnDetailSheet";
import { useMyReturns } from "@/hooks/useMyReturns";
import { useCancelReturn } from "@/hooks/useCancelReturn";
import { getCurrency } from "@/config/currencies";
import { formatDate } from "@/utils/formatters";
import type { ReturnSummaryResponse, ReturnStatus } from "@/types/redemption-returns.types";

const RETURN_STATUS_OPTIONS: { value: ReturnStatus; label: string }[] = [
  { value: "PENDING_APPROVAL", label: "Pending Approval" },
  { value: "APPROVED", label: "Approved" },
  { value: "RETURN_CONFIRMED", label: "Return Confirmed" },
  { value: "RETURN_REJECTED", label: "Return Rejected" },
  { value: "CANCELLED", label: "Cancelled" },
  { value: "RETURN_TIMED_OUT", label: "Timed Out" },
];

export function MyReturnsTab() {
  const [page, setPage] = useState(0);
  const [statusFilter, setStatusFilter] = useState<ReturnStatus | undefined>(undefined);
  const [selectedReturnId, setSelectedReturnId] = useState<string | null>(null);
  const [cancelTargetId, setCancelTargetId] = useState<string | null>(null);
  const submittingRef = useRef(false);

  const { data, isLoading, isError, refetch } = useMyReturns({ page, size: 20, status: statusFilter });
  const { mutateAsync: cancelMutateAsync, isPending: isCancelPending } = useCancelReturn();

  const rows: ReturnSummaryResponse[] = data?.data ?? [];
  const pagination = data
    ? {
        page: data.page,
        totalPages: data.totalPages,
        hasNext: data.hasNext,
        hasPrevious: data.hasPrevious,
        totalElements: data.totalElements,
      }
    : null;

  const handleCancelConfirm = useCallback(async () => {
    if (!cancelTargetId || submittingRef.current) return;
    submittingRef.current = true;
    try {
      await cancelMutateAsync(cancelTargetId);
      setCancelTargetId(null);
    } finally {
      submittingRef.current = false;
    }
  }, [cancelTargetId, cancelMutateAsync]);

  if (isLoading) {
    return (
      <div className="rounded-xl border border-border overflow-hidden" role="status" aria-busy="true" aria-label="Loading return requests">
        <Table>
          <TableHeader>
            <TableRow className="bg-muted/40">
              {["Catalog Item", "Amount", "Status", "Submitted", "Actions"].map((col) => (
                <TableHead key={col} scope="col" className="text-xs font-semibold text-muted-foreground">
                  {col}
                </TableHead>
              ))}
            </TableRow>
          </TableHeader>
          <TableBody>
            {Array.from({ length: 5 }).map((_, i) => (
              <TableRow key={i}>
                {Array.from({ length: 5 }).map((__, j) => (
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
        <p className="text-sm font-medium text-foreground" role="alert" aria-live="assertive">
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
      <div className="flex flex-col gap-4">
        {/* Status filter */}
        <div className="flex items-center gap-2">
          <Select
            value={statusFilter ?? ""}
            onValueChange={(v) => {
              setStatusFilter(v === "__all__" ? undefined : v as ReturnStatus);
              setPage(0);
            }}
          >
            <SelectTrigger className="h-9 w-[180px] rounded-lg text-sm" aria-label="Status filter">
              <SelectValue placeholder="All statuses" />
            </SelectTrigger>
            <SelectContent className="rounded-xl">
              <SelectItem value="__all__">All statuses</SelectItem>
              {RETURN_STATUS_OPTIONS.map((s) => (
                <SelectItem key={s.value} value={s.value}>{s.label}</SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <div className="rounded-xl border border-border overflow-x-auto">
          <Table>
            <TableHeader>
              <TableRow className="bg-muted/40">
                <TableHead scope="col" className="text-xs font-semibold text-muted-foreground">Catalog Item</TableHead>
                <TableHead scope="col" className="text-xs font-semibold text-muted-foreground">Amount</TableHead>
                <TableHead scope="col" className="text-xs font-semibold text-muted-foreground">Status</TableHead>
                <TableHead scope="col" className="text-xs font-semibold text-muted-foreground">Submitted</TableHead>
                <TableHead scope="col" className="text-xs font-semibold text-muted-foreground">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={5}>
                    <div className="flex flex-col items-center justify-center py-16 gap-3" role="status">
                      <InboxIcon className="h-8 w-8 text-muted-foreground" aria-hidden />
                      <p className="text-sm font-medium text-foreground">
                        {statusFilter ? "No return requests match the selected status." : "You have no return requests yet."}
                      </p>
                    </div>
                  </TableCell>
                </TableRow>
              ) : null}
              {rows.map((row) => {
                const isCancelling = isCancelPending && cancelTargetId === row.id;
                return (
                  <TableRow
                    key={row.id}
                    className="hover:bg-muted/30 transition-colors cursor-pointer"
                    onClick={() => setSelectedReturnId(row.id)}
                    role="button"
                    tabIndex={0}
                    onKeyDown={(e) => {
                      if (e.key !== "Enter" && e.key !== " ") return;
                      e.preventDefault();
                      setSelectedReturnId(row.id);
                    }}
                  >
                    <TableCell className="text-sm font-medium text-foreground">
                      {row.catalogItemName}
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
                    <TableCell onClick={(e) => e.stopPropagation()}>
                      {row.status === "PENDING_APPROVAL" ? (
                        <Button
                          variant="outline"
                          size="sm"
                          className="h-7 text-xs border-destructive/40 text-destructive hover:bg-destructive/10 hover:text-destructive"
                          disabled={isCancelling}
                          aria-label={`Cancel Return for ${row.catalogItemName}`}
                          onClick={() => setCancelTargetId(row.id)}
                        >
                          {isCancelling ? <Loader2 className="h-3 w-3 animate-spin" /> : "Cancel Return"}
                        </Button>
                      ) : null}
                    </TableCell>
                  </TableRow>
                );
              })}
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

      {/* Cancel confirmation AlertDialog */}
      <AlertDialog open={cancelTargetId !== null} onOpenChange={(open) => { if (!open && !isCancelPending) setCancelTargetId(null); }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Cancel this return request?</AlertDialogTitle>
            <AlertDialogDescription>
              This return request will be cancelled. You can submit a new request for the same redemption later.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={isCancelPending}>
              Keep request
            </AlertDialogCancel>
            <AlertDialogAction
              onClick={handleCancelConfirm}
              disabled={isCancelPending}
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
            >
              {isCancelPending ? <Loader2 className="h-4 w-4 animate-spin mr-2" /> : null}
              Yes, cancel it
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      {/* Return Detail Sheet */}
      <ReturnDetailSheet
        returnId={selectedReturnId}
        role="partner"
        open={selectedReturnId !== null}
        onClose={() => setSelectedReturnId(null)}
      />
    </>
  );
}
