import { Badge } from "@/components/ui/badge";
import type {
  DistributionItemStatus,
  DistributionRollupStatus,
} from "@/types/company-distribution.types";

/**
 * One badge for both the rollup and the per-recipient status, so the same word never looks different on
 * two screens.
 *
 * <p>`PARTIALLY_COMPLETED` gets its own treatment rather than reading as success — it means some
 * recipients were paid and others were released back to the company wallet, which the admin needs to
 * notice.</p>
 */
export function DistributionStatusBadge({
  status,
}: {
  status: DistributionRollupStatus | DistributionItemStatus;
}) {
  const { label, variant, className } = present(status);
  return (
    <Badge variant={variant} className={className} data-testid={`status-${status}`}>
      {label}
    </Badge>
  );
}

function present(status: string): {
  label: string;
  variant: "default" | "secondary" | "destructive" | "outline";
  className?: string;
} {
  switch (status) {
    case "COMPLETED":
      return { label: "Completed", variant: "default" };
    case "PARTIALLY_COMPLETED":
      return {
        label: "Partly completed",
        variant: "outline",
        className: "border-amber-500 text-amber-600 dark:text-amber-400",
      };
    case "PROCESSING":
      return { label: "Processing", variant: "secondary" };
    case "RESERVED":
      return { label: "Reserved", variant: "secondary" };
    case "FAILED":
      return { label: "Failed", variant: "destructive" };
    case "CANCELLED":
      return { label: "Cancelled", variant: "outline" };
    default:
      return { label: status, variant: "outline" };
  }
}
