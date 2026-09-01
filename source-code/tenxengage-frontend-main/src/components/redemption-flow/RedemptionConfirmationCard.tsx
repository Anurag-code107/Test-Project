// Adapted from: none — no production reference
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { getCurrency } from "@/config/currencies";
import type { RedemptionRequestDetailResponse } from "@/types/redemption-flow.types";

interface RedemptionConfirmationCardProps {
  redemption: RedemptionRequestDetailResponse;
}

function fmtDate(raw: string): string {
  const d = new Date(raw);
  return isNaN(d.getTime()) ? raw : d.toLocaleDateString("en-US", { dateStyle: "long" });
}

function deliveryText(redemption: RedemptionRequestDetailResponse): string {
  if (redemption.processingMode === "BATCH" && redemption.scheduledBatchDate) {
    return `Scheduled for processing on ${fmtDate(redemption.scheduledBatchDate)}`;
  }
  if (redemption.processingMode === "APPROVAL_REQUIRED") {
    return "Your redemption is pending approval";
  }
  if (redemption.estimatedDelivery) {
    return `Estimated delivery: ${fmtDate(redemption.estimatedDelivery)}`;
  }
  return "Your redemption has been submitted.";
}

export function RedemptionConfirmationCard({ redemption }: RedemptionConfirmationCardProps) {
  return (
    <Card className="w-full max-w-md">
      <CardHeader>
        <CardTitle>Redemption Submitted</CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        <div className="text-sm text-muted-foreground" data-testid="delivery-text">
          {deliveryText(redemption)}
        </div>
        <div className="space-y-1 text-sm">
          <div className="flex justify-between">
            <span className="text-muted-foreground">Item</span>
            <span className="font-medium">{redemption.catalogItemName}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-muted-foreground">Amount</span>
            <span className="font-medium">
              {getCurrency(redemption.currencyId).rewardFormat(redemption.amount)}
            </span>
          </div>
          <div className="flex justify-between">
            <span className="text-muted-foreground">Status</span>
            <span className="font-medium">{redemption.status}</span>
          </div>
        </div>
      </CardContent>
    </Card>
  );
}
