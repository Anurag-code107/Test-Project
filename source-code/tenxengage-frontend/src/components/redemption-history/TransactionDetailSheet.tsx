// Adapted from: none — no production reference
import { X } from "lucide-react";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetClose,
} from "@/components/ui/sheet";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";
import { getCurrency } from "@/config/currencies";
import { useRedemptionDetail } from "@/hooks/redemption-history/useRedemptionDetail";
import type { RedemptionStatus } from "@/types/redemption-history/redemption-history.types";

interface TransactionDetailSheetProps {
  redemptionId: string | null;
  open: boolean;
  onClose: () => void;
}

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
  return new Date(iso).toLocaleString(undefined, {
    year: "numeric", month: "short", day: "numeric",
    hour: "2-digit", minute: "2-digit",
  });
}

function Row({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex justify-between gap-4 py-2.5 border-b border-border last:border-0">
      <span className="text-sm text-muted-foreground shrink-0">{label}</span>
      <span className="text-sm text-foreground text-right">{value}</span>
    </div>
  );
}

export function TransactionDetailSheet({ redemptionId, open, onClose }: TransactionDetailSheetProps) {
  const { data, isLoading, isError } = useRedemptionDetail(redemptionId);

  return (
    <Sheet open={open} onOpenChange={(o) => { if (!o) onClose(); }}>
      <SheetContent className="w-full sm:max-w-[480px] overflow-y-auto [&>button:last-child]:hidden">
        <SheetHeader className="flex flex-row items-center justify-between mb-6">
          <SheetTitle>Transaction detail</SheetTitle>
          <SheetClose asChild>
            <button
              className="rounded-md p-1 hover:bg-muted transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
              aria-label="Close transaction detail"
            >
              <X className="h-4 w-4" />
            </button>
          </SheetClose>
        </SheetHeader>

        {isLoading && (
          <div className="space-y-3">
            {Array.from({ length: 7 }).map((_, i) => (
              <Skeleton key={i} className="h-8 w-full rounded" />
            ))}
          </div>
        )}

        {isError && (
          <p className="text-sm text-destructive">Could not load transaction details</p>
        )}

        {data && (
          <div className="flex flex-col">
            <Row label="Status" value={
              <Badge
                variant="outline"
                className={cn("text-xs border-0", STATUS_BADGE[data.status].className)}
              >
                {STATUS_BADGE[data.status].label}
              </Badge>
            } />
            <Row label="Item" value={data.catalogItemName} />
            <Row label="Amount" value={getCurrency(data.currencyId).rewardFormat(data.amount)} />
            <Row label="Currency" value={data.currencyId.toUpperCase()} />
            <Row label="Wallet" value={data.walletType === "INDIVIDUAL" ? "Individual" : "Company"} />
            <Row label="Processing" value={data.processingMode.replace("_", " ")} />
            <Row label="Submitted" value={formatDate(data.submittedAt)} />
            {data.scheduledBatchDate && (
              <Row label="Scheduled batch" value={data.scheduledBatchDate} />
            )}
            {(data.status === "COMPLETED" || data.status === "FAILED") && (
              <Row label="Completed" value={formatDate(data.completedAt)} />
            )}
            {data.status === "COMPLETED" && data.vendorReferenceId && (
              <Row label="Payment Transaction ID" value={data.vendorReferenceId} />
            )}
            {data.status === "FAILED" && data.failureReason && (
              <Row label="Failure reason" value={data.failureReason} />
            )}
            {data.status === "CANCELLED" && data.rejectionReason && (
              <Row label="Rejection reason" value={data.rejectionReason} />
            )}
            {data.reviewedBy && (
              <Row label="Reviewed by" value={data.reviewedByName ?? data.reviewedBy} />
            )}
            {data.reviewedAt && (
              <Row label="Reviewed at" value={formatDate(data.reviewedAt)} />
            )}
            {data.linkedReturnId && (
              <Row label="Linked return" value={data.linkedReturnId} />
            )}
          </div>
        )}
      </SheetContent>
    </Sheet>
  );
}
