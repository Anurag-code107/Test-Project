import { useState } from "react";
import { useSearchParams } from "react-router-dom";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import { CatalogBrowseGrid } from "@/components/redemption-catalog/CatalogBrowseGrid";
import { CatalogItemDetailSheet } from "@/components/redemption-catalog/CatalogItemDetailSheet";
import { BankTransferPanel } from "@/components/redemption-catalog/BankTransferPanel";
import { GiftCardEnrollmentNotice } from "@/components/redemption-catalog/GiftCardEnrollmentNotice";
import { useGiftCardPayoutReadiness } from "@/hooks/redemption-payout/useRedemptionProfile";

type StoreMode = "giftcard" | "bank";

export default function RedemptionStorePage() {
  const [selectedItemId, setSelectedItemId] = useState<string | null>(null);
  const [searchParams, setSearchParams] = useSearchParams();
  // Gift cards pay out through the XTRM payout profile, so with no profile set up nothing in the
  // catalog is redeemable — render the cards inactive rather than letting a user pick one and fail.
  const { isReady: payoutReady } = useGiftCardPayoutReadiness();

  // Mode persists in ?mode=bank|giftcard so refresh/deep-link survive. Default: gift card.
  const requestedMode: StoreMode = searchParams.get("mode") === "bank" ? "bank" : "giftcard";
  // Bank transfer needs a linked bank, and linking one needs the payout profile first (it creates the
  // XTRM beneficiary). With no profile the tab is disabled — and a ?mode=bank deep-link or a stale
  // bookmark falls back to gift cards rather than opening a panel whose only action is impossible.
  const bankAllowed = payoutReady;
  const mode: StoreMode = requestedMode === "bank" && !bankAllowed ? "giftcard" : requestedMode;

  const setMode = (next: string) => {
    if (next === "bank" && !bankAllowed) return;
    setSearchParams(
      (prev) => {
        const params = new URLSearchParams(prev);
        params.set("mode", next);
        return params;
      },
      { replace: true },
    );
  };

  return (
    <div className="animate-route-in p-6 space-y-6" data-testid="redemption-store-page">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold">Redemption Store</h1>
          <p className="text-sm text-muted-foreground mt-1">
            {mode === "bank"
              ? "Redeem your cash balance directly to your linked bank account."
              : "Browse available rewards and redeem your balance."}
          </p>
        </div>
        <Tabs value={mode} onValueChange={setMode}>
          <TabsList>
            <TabsTrigger value="giftcard">Gift Card</TabsTrigger>
            {bankAllowed ? (
              <TabsTrigger value="bank" data-testid="bank-mode-tab">
                Bank Transfer
              </TabsTrigger>
            ) : (
              <TooltipProvider>
                <Tooltip>
                  <TooltipTrigger asChild>
                    {/* A disabled trigger is pointer-events:none, so the tooltip needs a hoverable
                        wrapper — same pattern as the Redeem button in CatalogItemDetailSheet. */}
                    <span className="inline-flex">
                      <TabsTrigger value="bank" disabled data-testid="bank-mode-tab">
                        Bank Transfer
                      </TabsTrigger>
                    </span>
                  </TooltipTrigger>
                  <TooltipContent>Set up payouts to transfer to your bank.</TooltipContent>
                </Tooltip>
              </TooltipProvider>
            )}
          </TabsList>
        </Tabs>
      </div>

      {mode === "bank" ? (
        <BankTransferPanel />
      ) : (
        <>
          <GiftCardEnrollmentNotice />
          <CatalogBrowseGrid
            disabledReason={payoutReady ? null : "Set up payouts to redeem gift cards."}
            onItemClick={(id) => setSelectedItemId(id)}
          />

          <CatalogItemDetailSheet
            itemId={selectedItemId ?? ""}
            open={!!selectedItemId}
            onOpenChange={(open) => {
              if (!open) setSelectedItemId(null);
            }}
          />
        </>
      )}
    </div>
  );
}
