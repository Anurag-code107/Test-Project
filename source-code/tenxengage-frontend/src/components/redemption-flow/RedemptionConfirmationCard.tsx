// Adapted from: none — no production reference
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { getCurrency } from "@/config/currencies";
import { CatalogCardIllustration } from "@/components/redemption-catalog/CatalogCardIllustration";
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
      <CardContent className="space-y-4">
        <div className="flex gap-4">
          {/* Left: illustration + item name */}
          <div className="flex flex-col items-center gap-2 flex-shrink-0">
            <div className="w-24 h-24 rounded-lg overflow-hidden border">
              <CatalogCardIllustration
                category={redemption.category}
                imageUrl={redemption.imageUrl}
                catalogItemId={redemption.catalogItemId}
                providerImageUrl={redemption.providerImageUrl}
              />
            </div>
            <p className="text-xs font-medium text-center max-w-[96px] leading-tight" data-testid="item-name">
              {redemption.catalogItemName}
            </p>
          </div>

          {/* Right: details */}
          <div className="flex-1 space-y-2 text-sm pt-1">
            <div className="text-muted-foreground text-xs" data-testid="delivery-text">
              {deliveryText(redemption)}
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
        </div>
      </CardContent>
    </Card>
  );
}
