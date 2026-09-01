// Adapted from: src/components/redemption-history/TransactionDetailSheet.tsx (production analog from Mirror)
import { useRef, useState } from "react";
import { X, Loader2 } from "lucide-react";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetClose,
} from "@/components/ui/sheet";
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
import { Skeleton } from "@/components/ui/skeleton";
import { Button } from "@/components/ui/button";
import { ReturnStatusBadge } from "@/components/redemption-returns/ReturnStatusBadge";
import { RejectReturnDialog } from "@/components/redemption-returns/RejectReturnDialog";
import { ResolveTimedOutDialog } from "@/components/redemption-returns/ResolveTimedOutDialog";
import { useReturn } from "@/hooks/useReturn";
import { useCancelReturn } from "@/hooks/useCancelReturn";
import { useApproveReturn } from "@/hooks/useApproveReturn";
import { usePermissions } from "@/hooks/usePermissions";
import { getCurrency } from "@/config/currencies";
import { formatDate, formatDateTime } from "@/utils/formatters";

const RETURN_REVIEW_PERMISSION = "action.redemption.return.review";

interface ReturnDetailSheetProps {
  returnId: string | null;
  role: "partner" | "admin";
  open: boolean;
  onClose: () => void;
}

function Row({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex justify-between gap-4 py-2.5 border-b border-border last:border-0">
      <span className="text-sm text-muted-foreground shrink-0">{label}</span>
      <span className="text-sm text-foreground text-right">{value ?? "—"}</span>
    </div>
  );
}

export function ReturnDetailSheet({ returnId, role, open, onClose }: ReturnDetailSheetProps) {
  const isAdmin = role === "admin";
  const { can } = usePermissions();
  const canReview = can(RETURN_REVIEW_PERMISSION);

  const { data, isLoading, isError, refetch } = useReturn(returnId, isAdmin);
  const cancelMutation = useCancelReturn();
  const approveMutation = useApproveReturn();
  const approveRef = useRef(false);
  const submittingRef = useRef(false);

  const [approveDialogOpen, setApproveDialogOpen] = useState(false);
  const [rejectDialogOpen, setRejectDialogOpen] = useState(false);
  const [resolveDialogOpen, setResolveDialogOpen] = useState(false);

  const handleCancel = async () => {
    if (!returnId || submittingRef.current) return;
    submittingRef.current = true;
    try {
      await cancelMutation.mutateAsync(returnId);
      onClose();
    } finally {
      submittingRef.current = false;
    }
  };

  const handleApproveConfirm = async () => {
    if (!returnId || approveRef.current) return;
    approveRef.current = true;
    try {
      await approveMutation.mutateAsync(returnId);
      setApproveDialogOpen(false);
      onClose();
    } finally {
      approveRef.current = false;
    }
  };

  return (
    <>
    <Sheet open={open} onOpenChange={(o) => { if (!o) onClose(); }}>
      <SheetContent className="w-full sm:max-w-[520px] overflow-y-auto [&>button:last-child]:hidden">
        <SheetHeader className="flex flex-row items-center justify-between mb-6">
          <SheetTitle className="min-w-0 truncate">Return details</SheetTitle>
          <SheetClose asChild>
            <Button variant="ghost" size="icon" className="h-7 w-7" aria-label="Close return details">
              <X className="h-4 w-4" />
            </Button>
          </SheetClose>
        </SheetHeader>

        {!isLoading && !isError && !data && (
          <div className="min-h-[200px]" />
        )}

        {isLoading && (
          <div className="space-y-3 min-h-[200px]" role="status" aria-busy="true" aria-label="Loading return details">
            {Array.from({ length: 8 }).map((_, i) => (
              <Skeleton key={i} className="h-8 w-full rounded" />
            ))}
          </div>
        )}

        {isError && (
          <div className="flex flex-col items-center gap-3 py-10" role="alert" aria-live="assertive">
            <p className="text-sm text-destructive">Unable to load return details.</p>
            <Button variant="outline" onClick={() => refetch()}>Try again</Button>
          </div>
        )}

        {data && (
          <div className="flex flex-col">
            {/* Header — item name, status, amount */}
            <div className="pb-4 mb-2 border-b border-border">
              <h3 className="text-base font-semibold text-foreground mb-1">{data.catalogItemName}</h3>
              <div className="flex items-center gap-2">
                <ReturnStatusBadge status={data.status} />
                <span className="text-sm text-muted-foreground tabular-nums">
                  {getCurrency(data.currencyId).rewardFormat(data.amount)}
                </span>
              </div>
            </div>

            {/* Return info */}
            <Row label="Requested by" value={data.partnerDisplayName} />
            <Row label="Submitted" value={formatDateTime(data.createdAt)} />
            {data.reason ? (
              <Row label="Reason" value={data.reason} />
            ) : (
              <Row label="Reason" value="—" />
            )}

            {/* Timeline — status timestamps */}
            {data.approvedAt && (
              <Row label="Approved" value={formatDate(data.approvedAt)} />
            )}
            {data.confirmedAt && (
              <Row label="Confirmed" value={formatDate(data.confirmedAt)} />
            )}
            {data.rejectedAt && (
              <Row label="Rejected" value={formatDate(data.rejectedAt)} />
            )}
            {data.cancelledAt && (
              <Row label="Cancelled" value={formatDate(data.cancelledAt)} />
            )}
            {data.timedOutAt && (
              <Row label="Timed out" value={formatDate(data.timedOutAt)} />
            )}
            {data.reviewedAt && (
              <Row label="Reviewed" value={formatDate(data.reviewedAt)} />
            )}

            {/* Admin-only fields */}
            {isAdmin && data.reviewNotes && (
              <Row label="Admin notes" value={data.reviewNotes} />
            )}
            {isAdmin && data.vendorReturnReference && (
              <Row label="Vendor reference" value={data.vendorReturnReference} />
            )}
          </div>
        )}

        {/* Partner footer — cancel button for PENDING_APPROVAL */}
        {data && !isAdmin && data.status === "PENDING_APPROVAL" && (
          <div className="mt-6 pt-4 border-t border-border">
            <Button
              variant="destructive"
              className="w-full"
              onClick={handleCancel}
              disabled={cancelMutation.isPending}
            >
              {cancelMutation.isPending ? <Loader2 className="h-4 w-4 animate-spin mr-2" /> : null}
              Cancel Return
            </Button>
          </div>
        )}

        {/* Admin action panel — PENDING_APPROVAL: Approve + Reject */}
        {data && isAdmin && canReview && data.status === "PENDING_APPROVAL" && (
          <div className="mt-6 pt-4 border-t border-border flex flex-col gap-2">
            <Button
              variant="default"
              className="w-full"
              onClick={() => setApproveDialogOpen(true)}
              disabled={approveMutation.isPending}
              aria-label="Approve this return request"
            >
              {approveMutation.isPending ? <Loader2 className="h-4 w-4 animate-spin mr-2" /> : null}
              Approve
            </Button>
            <Button
              variant="destructive"
              className="w-full"
              onClick={() => setRejectDialogOpen(true)}
              aria-label="Reject this return request"
            >
              Reject
            </Button>
          </div>
        )}

        {/* Admin action panel — RETURN_TIMED_OUT: Resolve */}
        {data && isAdmin && canReview && data.status === "RETURN_TIMED_OUT" && (
          <div className="mt-6 pt-4 border-t border-border">
            <Button
              variant="outline"
              className="w-full"
              onClick={() => setResolveDialogOpen(true)}
              aria-label="Resolve timed-out return"
            >
              Resolve
            </Button>
          </div>
        )}
      </SheetContent>
    </Sheet>

    {/* Approve AlertDialog */}
    <AlertDialog open={approveDialogOpen} onOpenChange={setApproveDialogOpen}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Approve this return request?</AlertDialogTitle>
          <AlertDialogDescription>
            The return will be forwarded to Xoxoday. The partner&apos;s balance will be
            restored only after vendor confirmation.
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel disabled={approveMutation.isPending}>Cancel</AlertDialogCancel>
          <AlertDialogAction
            onClick={handleApproveConfirm}
            disabled={approveMutation.isPending}
          >
            {approveMutation.isPending ? (
              <Loader2 className="h-4 w-4 animate-spin mr-2" />
            ) : null}
            Approve
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>

    {/* Reject Dialog */}
    {returnId && (
      <RejectReturnDialog
        returnId={returnId}
        open={rejectDialogOpen}
        onOpenChange={setRejectDialogOpen}
        onSuccess={() => {
          setRejectDialogOpen(false);
          onClose();
        }}
      />
    )}

    {/* Resolve Timed-Out Dialog */}
    {returnId ? (
      <ResolveTimedOutDialog
        returnId={returnId}
        open={resolveDialogOpen}
        onOpenChange={setResolveDialogOpen}
        onSuccess={() => onClose()}
      />
    ) : null}
    </>
  );
}
