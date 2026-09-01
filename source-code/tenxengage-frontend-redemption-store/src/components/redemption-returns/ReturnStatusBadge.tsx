// Adapted from: src/components/redemption-history/TransactionHistoryTable.tsx (production analog from Mirror)
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import type { ReturnStatus } from "@/types/redemption-returns.types";

interface ReturnStatusBadgeProps {
  status: ReturnStatus;
  className?: string;
}

const STATUS_STYLES: Record<ReturnStatus, { label: string; className: string }> = {
  PENDING_APPROVAL: {
    label: "Pending Approval",
    className: "bg-warning/10 text-warning",
  },
  APPROVED: {
    label: "Approved",
    className: "bg-primary/10 text-primary",
  },
  RETURN_CONFIRMED: {
    label: "Return Confirmed",
    className: "bg-success/10 text-success",
  },
  RETURN_REJECTED: {
    label: "Return Rejected",
    className: "bg-destructive/10 text-destructive",
  },
  CANCELLED: {
    label: "Cancelled",
    className: "bg-muted text-muted-foreground",
  },
  RETURN_TIMED_OUT: {
    label: "Timed Out",
    className: "bg-warning/10 text-warning",
  },
};

export function ReturnStatusBadge({ status, className }: ReturnStatusBadgeProps) {
  const style = STATUS_STYLES[status] ?? {
    label: status,
    className: "bg-muted text-muted-foreground",
  };

  return (
    <Badge
      variant="outline"
      className={cn("text-xs font-medium border-0", style.className, className)}
    >
      {style.label}
    </Badge>
  );
}
